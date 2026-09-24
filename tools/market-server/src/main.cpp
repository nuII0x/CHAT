#include <arpa/inet.h>
#include <atomic>
#include <cerrno>
#include <csignal>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <mutex>
#include <netinet/in.h>
#include <sstream>
#include <string>
#include <sys/socket.h>
#include <thread>
#include <unistd.h>
#include "market_core.hpp"
#include "custody_registry.hpp"

namespace {
constexpr std::size_t kMaxRequestBytes = 256 * 1024;
constexpr int kBacklog = 64;
std::atomic<bool> running{true};
std::atomic<int> listening_socket{-1};
std::mutex package_mutex;

struct Config {
    std::string host = "127.0.0.1";
    int port = 8899;
    std::filesystem::path data = "market-data";
};

void on_signal(int) {
    running = false;
    const int socket = listening_socket.exchange(-1);
    if (socket >= 0) ::close(socket);
}

std::string json_escape(const std::string& value) {
    std::ostringstream out;
    for (unsigned char c : value) {
        if (c == '\\' || c == '"') out << '\\' << c;
        else if (c >= 0x20) out << c;
    }
    return out.str();
}

std::string response(int status, const std::string& body) {
    const char* label = status == 200 ? "OK" : status == 202 ? "Accepted" :
        status == 404 ? "Not Found" : status == 405 ? "Method Not Allowed" : "Bad Request";
    std::ostringstream out;
    out << "HTTP/1.1 " << status << ' ' << label << "\r\n"
        << "Content-Type: application/json\r\n"
        << "Cache-Control: no-store\r\n"
        << "X-Content-Type-Options: nosniff\r\n"
        << "Connection: close\r\n"
        << "Content-Length: " << body.size() << "\r\n\r\n" << body;
    return out.str();
}

bool valid_package(const std::string& body) {
    if (body.empty() || body.size() > 128 * 1024) return false;
    for (unsigned char c : body) {
        if (c == 0 || (c < 0x09) || (c > 0x0d && c < 0x20)) return false;
    }
    return true;
}

void append_package(const Config& config, const std::string& body) {
    std::lock_guard<std::mutex> lock(package_mutex);
    std::filesystem::create_directories(config.data);
    std::ofstream output(config.data / "opaque-packages.ndjson", std::ios::app);
    if (!output) throw std::runtime_error("package store unavailable");
    output << "{\"payload\":\"" << json_escape(body) << "\"}\n";
    output.flush();
    if (!output) throw std::runtime_error("package store write failed");
}

void handle_client(int client, const Config& config) {
    std::string request;
    char buffer[8192];
    while (request.size() < kMaxRequestBytes) {
        const auto count = ::recv(client, buffer, sizeof(buffer), 0);
        if (count <= 0) break;
        request.append(buffer, static_cast<std::size_t>(count));
        const auto header_end = request.find("\r\n\r\n");
        if (header_end != std::string::npos) {
            std::size_t length = 0;
            const auto marker = request.find("Content-Length:");
            if (marker != std::string::npos) {
                const auto start = marker + std::strlen("Content-Length:");
                length = static_cast<std::size_t>(std::stoul(request.substr(start)));
            }
            if (request.size() >= header_end + 4 + length) break;
        }
    }

    std::istringstream first_line(request.substr(0, request.find("\r\n")));
    std::string method, path, version;
    first_line >> method >> path >> version;
    std::string result;
    if (method == "GET" && path == "/v1/health") {
        result = response(200, "{\"ok\":true,\"transport\":\"onion-required\",\"ledgerReady\":true,\"matchingEngineReady\":true,\"accountRecoveryReady\":true,\"tradingEnabled\":false,\"custodyEnabled\":false}");
    } else if (method == "GET" && path == "/v1/market/snapshot") {
        result = response(200, "{\"mode\":\"simulation\",\"pairs\":[{\"symbol\":\"BTC/USDT\",\"lastMinor\":\"6428140000000\",\"scale\":8},{\"symbol\":\"XMR/USDT\",\"lastMinor\":\"17126000000\",\"scale\":8}]}");
    } else if (method == "POST" && path == "/v1/packages") {
        const auto split = request.find("\r\n\r\n");
        const std::string body = split == std::string::npos ? "" : request.substr(split + 4);
        if (!valid_package(body)) result = response(400, "{\"ok\":false,\"error\":\"invalid_package\"}");
        else {
            try {
                append_package(config, body);
                result = response(202, "{\"ok\":true,\"stored\":true}");
            } catch (...) {
                result = response(400, "{\"ok\":false,\"error\":\"store_unavailable\"}");
            }
        }
    } else if (path.rfind("/v1/", 0) == 0) {
        result = response(404, "{\"ok\":false,\"error\":\"not_found\"}");
    } else {
        result = response(400, "{\"ok\":false,\"error\":\"invalid_request\"}");
    }
    ::send(client, result.data(), result.size(), MSG_NOSIGNAL);
    ::close(client);
}

Config parse_config(int argc, char** argv) {
    Config config;
    for (int i = 1; i < argc; ++i) {
        const std::string arg = argv[i];
        if (arg == "--host" && i + 1 < argc) config.host = argv[++i];
        else if (arg == "--port" && i + 1 < argc) config.port = std::stoi(argv[++i]);
        else if (arg == "--data" && i + 1 < argc) config.data = argv[++i];
        else throw std::runtime_error("usage: null-market-server [--host 127.0.0.1] [--port 8899] [--data path]");
    }
    if (config.host != "127.0.0.1") throw std::runtime_error("server must bind to 127.0.0.1 and be exposed through Tor");
    if (config.port < 1 || config.port > 65535) throw std::runtime_error("invalid port");
    return config;
}
} // namespace

int main(int argc, char** argv) {
    try {
        const Config config = parse_config(argc, argv);
        std::filesystem::create_directories(config.data);
        nullmarket::Ledger ledger((config.data / "ledger.journal").string());
        nullmarket::MatchingEngine matching_engine(ledger, 100'000'000, 2'000);
        nullmarket::CustodyRegistry custody_registry((config.data / "custody-accounts.journal").string());
        (void)matching_engine;
        (void)custody_registry;
        std::signal(SIGINT, on_signal);
        std::signal(SIGTERM, on_signal);
        const int server = ::socket(AF_INET, SOCK_STREAM, 0);
        if (server < 0) throw std::runtime_error("socket failed");
        listening_socket = server;
        int reuse = 1;
        ::setsockopt(server, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));
        sockaddr_in address{};
        address.sin_family = AF_INET;
        address.sin_port = htons(static_cast<uint16_t>(config.port));
        ::inet_pton(AF_INET, config.host.c_str(), &address.sin_addr);
        if (::bind(server, reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0) {
            throw std::runtime_error(std::string("bind failed: ") + std::strerror(errno));
        }
        if (::listen(server, kBacklog) != 0) throw std::runtime_error("listen failed");
        std::cout << "Null Market listening locally on " << config.host << ':' << config.port
                  << " (trading and custody disabled)\n";
        while (running) {
            const int client = ::accept(server, nullptr, nullptr);
            if (client < 0) { if (errno == EINTR) continue; break; }
            std::thread(handle_client, client, std::cref(config)).detach();
        }
        const int socket = listening_socket.exchange(-1);
        if (socket >= 0) ::close(socket);
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "fatal: " << error.what() << '\n';
        return 1;
    }
}
