#include <arpa/inet.h>
#include <csignal>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <fcntl.h>
#include <fstream>
#include <iostream>
#include <map>
#include <netinet/in.h>
#include <random>
#include <regex>
#include <sstream>
#include <string>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <vector>

namespace {

constexpr std::size_t kMaxBodyBytes = 256 * 1024;
constexpr std::size_t kMaxEnvelopeBytes = 192 * 1024;
constexpr std::int64_t kMaxTtlMs = 7LL * 24LL * 60LL * 60LL * 1000LL;

volatile std::sig_atomic_t g_stop = 0;

void on_signal(int) {
    g_stop = 1;
}

std::int64_t now_ms() {
    return static_cast<std::int64_t>(std::time(nullptr)) * 1000LL;
}

std::string json_escape(const std::string& value) {
    std::ostringstream out;
    for (char ch : value) {
        switch (ch) {
            case '\\': out << "\\\\"; break;
            case '"': out << "\\\""; break;
            case '\n': out << "\\n"; break;
            case '\r': out << "\\r"; break;
            case '\t': out << "\\t"; break;
            default:
                if (static_cast<unsigned char>(ch) < 0x20) {
                    out << "\\u00";
                    const char* hex = "0123456789abcdef";
                    out << hex[(ch >> 4) & 0x0f] << hex[ch & 0x0f];
                } else {
                    out << ch;
                }
        }
    }
    return out.str();
}

std::string json_string_field(const std::string& json, const std::string& key) {
    const std::regex pattern("\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    std::smatch match;
    if (!std::regex_search(json, match, pattern)) return "";
    std::string value = match[1].str();
    std::string out;
    out.reserve(value.size());
    for (std::size_t i = 0; i < value.size(); ++i) {
        if (value[i] == '\\' && i + 1 < value.size()) {
            char next = value[++i];
            switch (next) {
                case 'n': out.push_back('\n'); break;
                case 'r': out.push_back('\r'); break;
                case 't': out.push_back('\t'); break;
                default: out.push_back(next); break;
            }
        } else {
            out.push_back(value[i]);
        }
    }
    return out;
}

std::int64_t json_int_field(const std::string& json, const std::string& key, std::int64_t fallback = 0) {
    const std::regex pattern("\"" + key + "\"\\s*:\\s*(-?[0-9]+)");
    std::smatch match;
    if (!std::regex_search(json, match, pattern)) return fallback;
    try {
        return std::stoll(match[1].str());
    } catch (...) {
        return fallback;
    }
}

std::string trim(const std::string& value) {
    const auto start = value.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return "";
    const auto end = value.find_last_not_of(" \t\r\n");
    return value.substr(start, end - start + 1);
}

std::string object_field(const std::string& json, const std::string& key) {
    const auto marker = "\"" + key + "\"";
    auto key_pos = json.find(marker);
    if (key_pos == std::string::npos) return "";
    auto colon = json.find(':', key_pos + marker.size());
    if (colon == std::string::npos) return "";
    auto open = json.find('{', colon + 1);
    if (open == std::string::npos) return "";
    int depth = 0;
    bool in_string = false;
    bool escaped = false;
    for (std::size_t i = open; i < json.size(); ++i) {
        char ch = json[i];
        if (escaped) {
            escaped = false;
            continue;
        }
        if (ch == '\\' && in_string) {
            escaped = true;
            continue;
        }
        if (ch == '"') {
            in_string = !in_string;
            continue;
        }
        if (in_string) continue;
        if (ch == '{') ++depth;
        if (ch == '}') {
            --depth;
            if (depth == 0) return json.substr(open, i - open + 1);
        }
    }
    return "";
}

std::string random_hex(std::size_t bytes) {
    std::random_device rd;
    std::mt19937_64 rng(rd());
    std::uniform_int_distribution<int> dist(0, 255);
    const char* hex = "0123456789abcdef";
    std::string out;
    out.reserve(bytes * 2);
    for (std::size_t i = 0; i < bytes; ++i) {
        int v = dist(rng);
        out.push_back(hex[(v >> 4) & 0x0f]);
        out.push_back(hex[v & 0x0f]);
    }
    return out;
}

void ensure_dir(const std::string& path) {
    if (path.empty()) return;
    mkdir(path.c_str(), 0755);
}

struct Envelope {
    std::string raw_json;
    std::string message_id;
    std::string recipient_hash;
    std::string ciphertext;
    std::string nonce;
    std::string proof;
    std::int64_t timestamp = 0;
    std::int64_t ttl = 0;
    std::int64_t stored_at = 0;

    bool expired(std::int64_t now) const {
        if (timestamp <= 0 || ttl <= 0) return true;
        return now > timestamp + ttl;
    }
};

struct HttpRequest {
    std::string method;
    std::string path;
    std::map<std::string, std::string> headers;
    std::string body;
};

class RelayStore {
public:
    explicit RelayStore(std::string dir)
        : dir_(std::move(dir)), store_file_(dir_ + "/envelopes.jsonl"), meta_file_(dir_ + "/relay.meta") {
        ensure_dir(dir_);
        load_meta();
        load_envelopes();
        purge_expired();
    }

    const std::string& relay_id() const { return relay_id_; }
    std::int64_t started_at() const { return started_at_; }
    std::int64_t accepted_count() const { return accepted_count_; }
    std::int64_t delivered_count() const { return delivered_count_; }

    std::size_t stored_count() const {
        return envelopes_.size();
    }

    std::pair<bool, std::string> upsert(Envelope envelope) {
        auto validation = validate(envelope);
        if (!validation.first) return validation;
        envelope.stored_at = now_ms();
        envelopes_[envelope.message_id] = std::move(envelope);
        ++accepted_count_;
        rewrite();
        save_meta();
        return {true, "stored"};
    }

    std::vector<Envelope> pull(const std::string& recipient_hash, int limit) {
        purge_expired();
        std::vector<Envelope> out;
        if (recipient_hash.empty()) return out;
        if (limit <= 0 || limit > 200) limit = 50;
        for (const auto& item : envelopes_) {
            if (item.second.recipient_hash == recipient_hash) {
                out.push_back(item.second);
                if (static_cast<int>(out.size()) >= limit) break;
            }
        }
        return out;
    }

    bool ack(const std::string& message_id, const std::string& recipient_hash) {
        auto it = envelopes_.find(message_id);
        if (it == envelopes_.end()) return false;
        if (it->second.recipient_hash != recipient_hash) return false;
        envelopes_.erase(it);
        ++delivered_count_;
        rewrite();
        save_meta();
        return true;
    }

    int purge_expired() {
        const auto now = now_ms();
        int removed = 0;
        for (auto it = envelopes_.begin(); it != envelopes_.end();) {
            if (it->second.expired(now)) {
                it = envelopes_.erase(it);
                ++removed;
            } else {
                ++it;
            }
        }
        if (removed > 0) rewrite();
        return removed;
    }

private:
    std::pair<bool, std::string> validate(const Envelope& envelope) const {
        if (envelope.raw_json.size() > kMaxEnvelopeBytes) return {false, "Envelope grande demais"};
        if (envelope.message_id.empty()) return {false, "messageId obrigatorio"};
        if (envelope.recipient_hash.empty()) return {false, "recipientPublicKeyHash obrigatorio"};
        if (envelope.ciphertext.empty()) return {false, "ciphertext obrigatorio"};
        if (envelope.nonce.empty()) return {false, "nonce obrigatorio"};
        if (envelope.timestamp <= 0) return {false, "timestamp invalido"};
        if (envelope.ttl <= 0 || envelope.ttl > kMaxTtlMs) return {false, "ttl invalido"};
        if (envelope.expired(now_ms())) return {false, "Envelope expirado"};
        if (envelope.message_id.size() > 160 || envelope.recipient_hash.size() > 160) {
            return {false, "Identificador grande demais"};
        }
        return {true, ""};
    }

    void load_meta() {
        started_at_ = now_ms();
        std::ifstream in(meta_file_);
        if (in) {
            std::string line;
            while (std::getline(in, line)) {
                auto pos = line.find('=');
                if (pos == std::string::npos) continue;
                auto key = line.substr(0, pos);
                auto value = line.substr(pos + 1);
                if (key == "relayId") relay_id_ = trim(value);
                if (key == "accepted") accepted_count_ = std::stoll(trim(value).empty() ? "0" : trim(value));
                if (key == "delivered") delivered_count_ = std::stoll(trim(value).empty() ? "0" : trim(value));
            }
        }
        if (relay_id_.empty()) {
            relay_id_ = random_hex(16);
            save_meta();
        }
    }

    void save_meta() const {
        std::ofstream out(meta_file_, std::ios::trunc);
        out << "relayId=" << relay_id_ << "\n";
        out << "accepted=" << accepted_count_ << "\n";
        out << "delivered=" << delivered_count_ << "\n";
    }

    void load_envelopes() {
        std::ifstream in(store_file_);
        std::string line;
        while (std::getline(in, line)) {
            auto envelope = parse_envelope(line);
            if (!envelope.message_id.empty()) {
                envelopes_[envelope.message_id] = std::move(envelope);
            }
        }
    }

    void rewrite() const {
        const std::string tmp = store_file_ + ".tmp";
        std::ofstream out(tmp, std::ios::trunc);
        for (const auto& item : envelopes_) {
            out << item.second.raw_json << "\n";
        }
        out.close();
        std::rename(tmp.c_str(), store_file_.c_str());
    }

public:
    static Envelope parse_envelope(const std::string& body) {
        std::string raw = object_field(body, "envelope");
        if (raw.empty()) raw = trim(body);
        Envelope envelope;
        envelope.raw_json = raw;
        envelope.message_id = json_string_field(raw, "messageId");
        envelope.recipient_hash = json_string_field(raw, "recipientPublicKeyHash");
        envelope.ciphertext = json_string_field(raw, "ciphertext");
        envelope.nonce = json_string_field(raw, "nonce");
        envelope.proof = json_string_field(raw, "proof");
        envelope.timestamp = json_int_field(raw, "timestamp");
        envelope.ttl = json_int_field(raw, "ttl");
        envelope.stored_at = json_int_field(raw, "storedAt", now_ms());
        return envelope;
    }

private:
    std::string dir_;
    std::string store_file_;
    std::string meta_file_;
    std::string relay_id_;
    std::int64_t started_at_ = 0;
    std::int64_t accepted_count_ = 0;
    std::int64_t delivered_count_ = 0;
    std::map<std::string, Envelope> envelopes_;
};

std::string http_response(int status, const std::string& body) {
    const char* text = "OK";
    if (status == 400) text = "Bad Request";
    if (status == 404) text = "Not Found";
    if (status == 413) text = "Payload Too Large";
    if (status == 500) text = "Internal Server Error";
    std::ostringstream out;
    out << "HTTP/1.1 " << status << " " << text << "\r\n";
    out << "Content-Type: application/json; charset=utf-8\r\n";
    out << "Content-Length: " << body.size() << "\r\n";
    out << "Connection: close\r\n\r\n";
    out << body;
    return out.str();
}

std::string ok_body(const std::string& fields) {
    return "{\"ok\":true" + (fields.empty() ? std::string() : "," + fields) + "}";
}

std::string error_body(const std::string& message) {
    return "{\"ok\":false,\"error\":\"" + json_escape(message) + "\"}";
}

bool read_request(int fd, HttpRequest& request) {
    std::string data;
    char buffer[4096];
    while (data.find("\r\n\r\n") == std::string::npos) {
        ssize_t n = recv(fd, buffer, sizeof(buffer), 0);
        if (n <= 0) return false;
        data.append(buffer, buffer + n);
        if (data.size() > kMaxBodyBytes + 8192) return false;
    }

    const auto header_end = data.find("\r\n\r\n");
    std::istringstream headers(data.substr(0, header_end));
    std::string request_line;
    std::getline(headers, request_line);
    request_line = trim(request_line);
    std::istringstream first(request_line);
    first >> request.method >> request.path;
    request.path = request.path.substr(0, request.path.find('?'));

    std::string line;
    int content_length = 0;
    while (std::getline(headers, line)) {
        line = trim(line);
        auto pos = line.find(':');
        if (pos == std::string::npos) continue;
        std::string key = line.substr(0, pos);
        std::string value = trim(line.substr(pos + 1));
        for (char& ch : key) ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
        request.headers[key] = value;
        if (key == "content-length") {
            content_length = std::atoi(value.c_str());
            if (content_length < 0 || static_cast<std::size_t>(content_length) > kMaxBodyBytes) return false;
        }
    }

    request.body = data.substr(header_end + 4);
    while (static_cast<int>(request.body.size()) < content_length) {
        ssize_t n = recv(fd, buffer, sizeof(buffer), 0);
        if (n <= 0) return false;
        request.body.append(buffer, buffer + n);
        if (request.body.size() > kMaxBodyBytes) return false;
    }
    if (static_cast<int>(request.body.size()) > content_length) {
        request.body.resize(content_length);
    }
    return true;
}

std::string handle_request(RelayStore& store, const HttpRequest& request) {
    store.purge_expired();
    if (request.method == "GET" && (request.path == "/" || request.path == "/v1/relay/status")) {
        const auto uptime = (now_ms() - store.started_at()) / 1000LL;
        std::ostringstream body;
        body << ok_body(
            "\"relayId\":\"" + json_escape(store.relay_id()) + "\"" +
            ",\"startedAt\":" + std::to_string(store.started_at()) +
            ",\"uptimeSeconds\":" + std::to_string(uptime) +
            ",\"storedEnvelopes\":" + std::to_string(store.stored_count()) +
            ",\"acceptedEnvelopes\":" + std::to_string(store.accepted_count()) +
            ",\"deliveredEnvelopes\":" + std::to_string(store.delivered_count()) +
            ",\"trustHint\":" + std::to_string(uptime)
        );
        return http_response(200, body.str());
    }

    if (request.method == "POST" && request.path == "/v1/relay/envelopes") {
        auto envelope = RelayStore::parse_envelope(request.body);
        auto result = store.upsert(std::move(envelope));
        if (!result.first) return http_response(400, error_body(result.second));
        return http_response(200, ok_body("\"status\":\"stored\""));
    }

    if (request.method == "POST" && request.path == "/v1/relay/pull") {
        const auto recipient = json_string_field(request.body, "recipientPublicKeyHash");
        int limit = static_cast<int>(json_int_field(request.body, "limit", 50));
        auto messages = store.pull(recipient, limit);
        std::ostringstream array;
        array << "[";
        for (std::size_t i = 0; i < messages.size(); ++i) {
            if (i > 0) array << ",";
            array << messages[i].raw_json;
        }
        array << "]";
        return http_response(200, ok_body("\"messages\":" + array.str()));
    }

    if (request.method == "POST" && request.path == "/v1/relay/ack") {
        const auto message_id = json_string_field(request.body, "messageId");
        const auto recipient = json_string_field(request.body, "recipientPublicKeyHash");
        bool removed = store.ack(message_id, recipient);
        return http_response(200, ok_body(std::string("\"removed\":") + (removed ? "true" : "false")));
    }

    return http_response(404, error_body("Rota nao encontrada"));
}

struct Options {
    std::string host = "127.0.0.1";
    int port = 8787;
    std::string data_dir = "relay-data";
};

Options parse_options(int argc, char** argv) {
    Options options;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        auto next = [&]() -> std::string {
            if (i + 1 >= argc) {
                std::cerr << "Valor ausente para " << arg << "\n";
                std::exit(2);
            }
            return argv[++i];
        };
        if (arg == "--host") options.host = next();
        else if (arg == "--port") options.port = std::atoi(next().c_str());
        else if (arg == "--data") options.data_dir = next();
        else if (arg == "--help" || arg == "-h") {
            std::cout << "Uso: mailbox-relay [--host 127.0.0.1] [--port 8787] [--data relay-data]\n";
            std::exit(0);
        } else {
            std::cerr << "Opcao desconhecida: " << arg << "\n";
            std::exit(2);
        }
    }
    if (options.port <= 0 || options.port > 65535) {
        std::cerr << "Porta invalida\n";
        std::exit(2);
    }
    return options;
}

int run_server(const Options& options) {
    RelayStore store(options.data_dir);
    int server = socket(AF_INET, SOCK_STREAM, 0);
    if (server < 0) {
        perror("socket");
        return 1;
    }
    int yes = 1;
    setsockopt(server, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(static_cast<uint16_t>(options.port));
    if (inet_pton(AF_INET, options.host.c_str(), &addr.sin_addr) != 1) {
        std::cerr << "Host IPv4 invalido: " << options.host << "\n";
        close(server);
        return 1;
    }
    if (bind(server, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) != 0) {
        perror("bind");
        close(server);
        return 1;
    }
    if (listen(server, 64) != 0) {
        perror("listen");
        close(server);
        return 1;
    }

    std::cout << "Mailbox relay " << store.relay_id() << " em http://" << options.host
              << ":" << options.port << "\n";
    std::cout << "Dados em " << options.data_dir << "\n";

    while (!g_stop) {
        sockaddr_in client_addr{};
        socklen_t len = sizeof(client_addr);
        int client = accept(server, reinterpret_cast<sockaddr*>(&client_addr), &len);
        if (client < 0) {
            if (g_stop) break;
            continue;
        }
        HttpRequest request;
        std::string response;
        if (!read_request(client, request)) {
            response = http_response(400, error_body("Requisicao invalida"));
        } else {
            response = handle_request(store, request);
        }
        send(client, response.data(), response.size(), 0);
        close(client);
    }

    close(server);
    std::cout << "Relay encerrado\n";
    return 0;
}

}  // namespace

int main(int argc, char** argv) {
    std::signal(SIGINT, on_signal);
    std::signal(SIGTERM, on_signal);
    auto options = parse_options(argc, argv);
    return run_server(options);
}
