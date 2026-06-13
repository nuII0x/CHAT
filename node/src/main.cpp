#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/sha.h>
#include <httplib.h>
#include <nlohmann/json.hpp>

#include <atomic>
#include <algorithm>
#include <cctype>
#include <chrono>
#include <cstring>
#include <csignal>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <iomanip>
#include <mutex>
#include <optional>
#include <sstream>
#include <string>
#include <thread>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include <arpa/inet.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <netdb.h>
#include <sys/socket.h>
#include <unistd.h>

using json = nlohmann::json;

namespace {
constexpr const char* kProtocolPrefix = "P2P_V1";
constexpr const char* kTypeEnvelope = "ENV";
constexpr const char* kTypePull = "PULL";
constexpr const char* kTypePullResponse = "PULL_RESP";
constexpr const char* kTypeAck = "ACK";
constexpr const char* kChatMessagePrefix = "CHAT_MSG|";
constexpr const char* kChatAckPrefix = "CHAT_ACK|";
constexpr const char* kChatPresencePrefix = "CHAT_PRESENCE|";
constexpr const char* kContactRequest = "[NullChat:contact-request]";
constexpr const char* kContactAccept = "[NullChat:contact-accept]";
constexpr const char* kSimpleCipherPrefix = "RS2:";
constexpr const char* kSimpleCipherKeySeed = "NullChat private transport message key v2";
constexpr long long kDefaultMaxTtlMs = 7LL * 24LL * 60LL * 60LL * 1000LL;
constexpr long long kFastRelayDefaultTtlMs = 10LL * 60LL * 1000LL;
constexpr long long kFastRelayMaxTtlMs = 60LL * 60LL * 1000LL;
constexpr int kChatProtocolPort = 5000;
constexpr int kNonceBytes = 12;
constexpr int kGcmTagBytes = 16;

std::atomic_bool g_running{true};

long long now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();
}

std::string env_or(const char* name, const std::string& fallback) {
    const char* value = std::getenv(name);
    return value == nullptr || std::string(value).empty() ? fallback : value;
}

int env_int_or(const char* name, int fallback) {
    const char* value = std::getenv(name);
    if (value == nullptr) return fallback;
    try {
        return std::stoi(value);
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

std::string json_string(const json& item, const char* key) {
    if (!item.contains(key) || item.at(key).is_null()) return "";
    if (!item.at(key).is_string()) return "";
    return trim(item.at(key).get<std::string>());
}

long long json_i64(const json& item, const char* key, long long fallback = 0) {
    if (!item.contains(key) || item.at(key).is_null()) return fallback;
    if (item.at(key).is_number_integer()) return item.at(key).get<long long>();
    if (item.at(key).is_number_unsigned()) return static_cast<long long>(item.at(key).get<unsigned long long>());
    if (item.at(key).is_string()) {
        try {
            return std::stoll(trim(item.at(key).get<std::string>()));
        } catch (...) {
            return fallback;
        }
    }
    return fallback;
}

std::string hex_sha256(const std::string& input) {
    unsigned char digest[SHA256_DIGEST_LENGTH];
    SHA256(reinterpret_cast<const unsigned char*>(input.data()), input.size(), digest);
    static constexpr char kHex[] = "0123456789abcdef";
    std::string out;
    out.reserve(SHA256_DIGEST_LENGTH * 2);
    for (unsigned char byte : digest) {
        out.push_back(kHex[(byte >> 4) & 0x0F]);
        out.push_back(kHex[byte & 0x0F]);
    }
    return out;
}

std::optional<std::vector<unsigned char>> base64_decode(const std::string& input) {
    static const std::string chars =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::vector<int> table(256, -1);
    for (int i = 0; i < static_cast<int>(chars.size()); ++i) {
        table[static_cast<unsigned char>(chars[i])] = i;
    }

    std::vector<unsigned char> out;
    int val = 0;
    int valb = -8;
    for (unsigned char c : input) {
        if (c == '=') break;
        if (std::isspace(c)) continue;
        if (table[c] == -1) return std::nullopt;
        val = (val << 6) + table[c];
        valb += 6;
        if (valb >= 0) {
            out.push_back(static_cast<unsigned char>((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return out;
}

std::string base64_encode(const std::string& input) {
    static const char* chars =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    int val = 0;
    int valb = -6;
    for (unsigned char c : input) {
        val = (val << 8) + c;
        valb += 8;
        while (valb >= 0) {
            out.push_back(chars[(val >> valb) & 0x3F]);
            valb -= 6;
        }
    }
    if (valb > -6) out.push_back(chars[((val << 8) >> (valb + 8)) & 0x3F]);
    while (out.size() % 4) out.push_back('=');
    return out;
}

std::string base64_encode_bytes(const std::vector<unsigned char>& input) {
    static const char* chars =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    int val = 0;
    int valb = -6;
    for (unsigned char c : input) {
        val = (val << 8) + c;
        valb += 8;
        while (valb >= 0) {
            out.push_back(chars[(val >> valb) & 0x3F]);
            valb -= 6;
        }
    }
    if (valb > -6) out.push_back(chars[((val << 8) >> (valb + 8)) & 0x3F]);
    while (out.size() % 4) out.push_back('=');
    return out;
}

std::vector<unsigned char> simple_cipher_key() {
    std::vector<unsigned char> digest(SHA256_DIGEST_LENGTH);
    SHA256(
        reinterpret_cast<const unsigned char*>(kSimpleCipherKeySeed),
        std::strlen(kSimpleCipherKeySeed),
        digest.data()
    );
    return digest;
}

std::optional<std::string> simple_encrypt(const std::string& plain) {
    const auto key = simple_cipher_key();
    std::vector<unsigned char> nonce(kNonceBytes);
    if (RAND_bytes(nonce.data(), static_cast<int>(nonce.size())) != 1) return std::nullopt;

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (ctx == nullptr) return std::nullopt;

    std::vector<unsigned char> cipher(plain.size() + kGcmTagBytes);
    int out_len = 0;
    int total_len = 0;
    bool ok = EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) == 1 &&
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, kNonceBytes, nullptr) == 1 &&
        EVP_EncryptInit_ex(ctx, nullptr, nullptr, key.data(), nonce.data()) == 1 &&
        EVP_EncryptUpdate(
            ctx,
            cipher.data(),
            &out_len,
            reinterpret_cast<const unsigned char*>(plain.data()),
            static_cast<int>(plain.size())
        ) == 1;
    total_len = out_len;
    ok = ok && EVP_EncryptFinal_ex(ctx, cipher.data() + total_len, &out_len) == 1;
    total_len += out_len;
    ok = ok && EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, kGcmTagBytes, cipher.data() + total_len) == 1;
    total_len += kGcmTagBytes;
    EVP_CIPHER_CTX_free(ctx);
    if (!ok) return std::nullopt;

    std::vector<unsigned char> packed;
    packed.reserve(nonce.size() + total_len);
    packed.insert(packed.end(), nonce.begin(), nonce.end());
    packed.insert(packed.end(), cipher.begin(), cipher.begin() + total_len);
    return std::string(kSimpleCipherPrefix) + base64_encode_bytes(packed);
}

std::optional<std::string> simple_decrypt(const std::string& cipher_text) {
    const auto clean = trim(cipher_text);
    if (clean.rfind(kSimpleCipherPrefix, 0) != 0) return std::nullopt;
    const auto decoded = base64_decode(clean.substr(std::strlen(kSimpleCipherPrefix)));
    if (!decoded || decoded->size() <= kNonceBytes + kGcmTagBytes) return std::nullopt;

    const auto key = simple_cipher_key();
    const unsigned char* nonce = decoded->data();
    const unsigned char* cipher = decoded->data() + kNonceBytes;
    const auto cipher_len = static_cast<int>(decoded->size() - kNonceBytes - kGcmTagBytes);
    const unsigned char* tag = decoded->data() + decoded->size() - kGcmTagBytes;

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (ctx == nullptr) return std::nullopt;

    std::vector<unsigned char> plain(cipher_len);
    int out_len = 0;
    int total_len = 0;
    bool ok = EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) == 1 &&
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, kNonceBytes, nullptr) == 1 &&
        EVP_DecryptInit_ex(ctx, nullptr, nullptr, key.data(), nonce) == 1 &&
        EVP_DecryptUpdate(ctx, plain.data(), &out_len, cipher, cipher_len) == 1;
    total_len = out_len;
    ok = ok && EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, kGcmTagBytes, const_cast<unsigned char*>(tag)) == 1 &&
        EVP_DecryptFinal_ex(ctx, plain.data() + total_len, &out_len) == 1;
    total_len += out_len;
    EVP_CIPHER_CTX_free(ctx);
    if (!ok) return std::nullopt;
    return std::string(reinterpret_cast<char*>(plain.data()), static_cast<size_t>(total_len));
}

std::string random_id() {
    unsigned char bytes[16];
    if (RAND_bytes(bytes, sizeof(bytes)) != 1) {
        return std::to_string(now_ms());
    }
    std::ostringstream out;
    for (unsigned char byte : bytes) {
        out << std::hex << std::setw(2) << std::setfill('0') << static_cast<int>(byte);
    }
    return out.str();
}

std::optional<std::string> base64_decode_to_string(const std::string& input) {
    const auto decoded = base64_decode(input);
    if (!decoded) return std::nullopt;
    return std::string(decoded->begin(), decoded->end());
}

bool verify_ed25519(const std::string& public_key_b64, const std::string& message, const std::string& signature_b64) {
    const auto public_key = base64_decode(public_key_b64);
    const auto signature = base64_decode(signature_b64);
    if (!public_key || !signature || public_key->size() != 32 || signature->size() != 64) return false;

    EVP_PKEY* key = EVP_PKEY_new_raw_public_key(EVP_PKEY_ED25519, nullptr, public_key->data(), public_key->size());
    if (key == nullptr) return false;

    EVP_MD_CTX* ctx = EVP_MD_CTX_new();
    if (ctx == nullptr) {
        EVP_PKEY_free(key);
        return false;
    }

    bool ok = false;
    if (EVP_DigestVerifyInit(ctx, nullptr, nullptr, nullptr, key) == 1) {
        ok = EVP_DigestVerify(
            ctx,
            signature->data(),
            signature->size(),
            reinterpret_cast<const unsigned char*>(message.data()),
            message.size()
        ) == 1;
    }

    EVP_MD_CTX_free(ctx);
    EVP_PKEY_free(key);
    return ok;
}

std::string envelope_canonical_string(const json& envelope) {
    std::ostringstream out;
    out << json_string(envelope, "messageId") << '|'
        << json_string(envelope, "recipientPublicKeyHash") << '|'
        << json_string(envelope, "recipientPublicKey") << '|'
        << json_string(envelope, "recipientExchangePublicKey") << '|'
        << json_string(envelope, "senderRoute") << '|'
        << json_string(envelope, "senderPublicKey") << '|'
        << json_string(envelope, "senderExchangePublicKey") << '|'
        << hex_sha256(json_string(envelope, "ciphertext")) << '|'
        << json_string(envelope, "nonce") << '|'
        << json_i64(envelope, "timestamp") << '|'
        << json_i64(envelope, "ttl");
    return out.str();
}

std::string ack_canonical_string(const json& ack) {
    std::ostringstream out;
    out << json_string(ack, "messageId") << '|'
        << json_string(ack, "recipientPublicKeyHash") << '|'
        << json_i64(ack, "timestamp");
    return out.str();
}

std::string relay_signed_request_canonical(
    const std::string& method,
    const std::string& path,
    const std::string& body,
    long long timestamp,
    const std::string& nonce
) {
    std::ostringstream out;
    std::string normalized_method = trim(method);
    std::transform(normalized_method.begin(), normalized_method.end(), normalized_method.begin(), [](unsigned char c) {
        return static_cast<char>(std::toupper(c));
    });
    out << normalized_method << '\n'
        << trim(path) << '\n'
        << hex_sha256(body) << '\n'
        << timestamp << '\n'
        << trim(nonce);
    return out.str();
}

class RelayAuthStore {
public:
    bool validate(const httplib::Request& request, const std::string& body, long long now = now_ms()) {
        const auto public_key = trim(request.get_header_value("X-Public-Key"));
        const auto signature = trim(request.get_header_value("X-Signature"));
        const auto nonce = trim(request.get_header_value("X-Nonce"));
        const auto timestamp = request.get_header_value("X-Timestamp");
        long long parsed_timestamp = 0;
        try {
            parsed_timestamp = std::stoll(timestamp);
        } catch (...) {
            return false;
        }
        if (public_key.empty() || signature.empty() || nonce.empty()) return false;
        if (std::llabs(now - parsed_timestamp) > 5 * 60 * 1000L) return false;
        if (nonce.size() < 8 || nonce.size() > 160) return false;

        const auto nonce_key = public_key + ":" + nonce;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            purge_locked(now);
            if (seen_nonces_.find(nonce_key) != seen_nonces_.end()) return false;
        }

        const auto canonical = relay_signed_request_canonical(
            request.method,
            request.path,
            body,
            parsed_timestamp,
            nonce
        );
        if (!verify_ed25519(public_key, canonical, signature)) return false;

        std::lock_guard<std::mutex> lock(mutex_);
        purge_locked(now);
        if (seen_nonces_.find(nonce_key) != seen_nonces_.end()) return false;
        seen_nonces_[nonce_key] = now;
        return true;
    }

private:
    void purge_locked(long long now) {
        for (auto it = seen_nonces_.begin(); it != seen_nonces_.end();) {
            if (now - it->second > 5 * 60 * 1000L) {
                it = seen_nonces_.erase(it);
            } else {
                ++it;
            }
        }
    }

    std::mutex mutex_;
    std::unordered_map<std::string, long long> seen_nonces_;
};

struct WirePacket {
    std::string type;
    json payload;
};

std::optional<WirePacket> decode_wire_packet(const std::string& wire) {
    const auto first = wire.find('|');
    const auto second = first == std::string::npos ? std::string::npos : wire.find('|', first + 1);
    if (first == std::string::npos || second == std::string::npos) return std::nullopt;
    if (wire.substr(0, first) != kProtocolPrefix) return std::nullopt;
    const auto type = wire.substr(first + 1, second - first - 1);
    const auto encoded_payload = wire.substr(second + 1);
    const auto decoded = base64_decode(encoded_payload);
    if (!decoded) return std::nullopt;
    const std::string payload_text(decoded->begin(), decoded->end());
    try {
        return WirePacket{type, json::parse(payload_text)};
    } catch (...) {
        return std::nullopt;
    }
}

std::string encode_wire_packet(const std::string& type, const json& payload) {
    return std::string(kProtocolPrefix) + "|" + type + "|" + base64_encode(payload.dump());
}

class EnvelopeStore {
public:
    explicit EnvelopeStore(std::filesystem::path data_dir)
        : data_dir_(std::move(data_dir)), storage_file_(data_dir_ / "messages.json") {
        std::filesystem::create_directories(data_dir_);
        load();
    }

    json stats() {
        std::lock_guard<std::mutex> lock(mutex_);
        purge_expired_locked();
        return {
            {"stored", records_.size()},
            {"storedEntries", records_.size()},
            {"dataFile", storage_file_.string()}
        };
    }

    std::pair<bool, std::string> upsert(const json& envelope) {
        std::lock_guard<std::mutex> lock(mutex_);
        purge_expired_locked();

        const auto validation = validate_envelope(envelope);
        if (validation) return {false, *validation};

        const auto message_id = json_string(envelope, "messageId");
        const auto sender_route = json_string(envelope, "senderRoute");
        records_[message_id] = EnvelopeRecord{
            .envelope = envelope,
            .received_at = now_ms(),
            .sender_route = sender_route,
            .recipient_hash = json_string(envelope, "recipientPublicKeyHash"),
            .ttl_ms = json_i64(envelope, "ttl")
        };
        persist_locked();
        return {true, "stored"};
    }

    json pull(const std::string& recipient_hash, long long since_timestamp, int limit) {
        std::lock_guard<std::mutex> lock(mutex_);
        purge_expired_locked();

        json result = json::array();
        std::vector<json> matches;
        for (const auto& [_, record] : records_) {
            const auto& envelope = record.envelope;
            if (record.recipient_hash != recipient_hash) continue;
            if (json_i64(envelope, "timestamp") < since_timestamp) continue;
            matches.push_back(envelope);
        }

        std::sort(matches.begin(), matches.end(), [](const json& a, const json& b) {
            return json_i64(a, "timestamp") < json_i64(b, "timestamp");
        });

        for (const auto& envelope : matches) {
            if (static_cast<int>(result.size()) >= limit) break;
            result.push_back(envelope);
        }
        return result;
    }

    std::pair<bool, std::string> ack(const json& ack_packet) {
        std::lock_guard<std::mutex> lock(mutex_);
        purge_expired_locked();

        const auto message_id = json_string(ack_packet, "messageId");
        const auto recipient_hash = json_string(ack_packet, "recipientPublicKeyHash");
        const auto signature = json_string(ack_packet, "signature");
        if (message_id.empty() || recipient_hash.empty() || signature.empty()) {
            return {false, "ack incompleto"};
        }

        auto item = records_.find(message_id);
        if (item == records_.end()) return {false, "mensagem nao encontrada"};

        const auto& envelope = item->second.envelope;
        if (json_string(envelope, "recipientPublicKeyHash") != recipient_hash) {
            return {false, "destinatario divergente"};
        }

        const auto recipient_public_key = json_string(envelope, "recipientPublicKey");
        if (recipient_public_key.empty()) return {false, "envelope sem chave publica do destinatario"};
        if (!verify_ed25519(recipient_public_key, ack_canonical_string(ack_packet), signature)) {
            return {false, "assinatura de ACK invalida"};
        }

        records_.erase(item);
        persist_locked();
        return {true, "acked"};
    }

private:
    std::optional<std::string> validate_envelope(const json& envelope) const {
        const auto message_id = json_string(envelope, "messageId");
        const auto recipient_hash = json_string(envelope, "recipientPublicKeyHash");
        const auto sender_public_key = json_string(envelope, "senderPublicKey");
        const auto ciphertext = json_string(envelope, "ciphertext");
        const auto nonce = json_string(envelope, "nonce");
        const auto proof = json_string(envelope, "proof");
        const auto timestamp = json_i64(envelope, "timestamp");
        const auto ttl = json_i64(envelope, "ttl");

        if (message_id.empty()) return "messageId ausente";
        if (recipient_hash.empty()) return "recipientPublicKeyHash ausente";
        if (sender_public_key.empty()) return "senderPublicKey ausente";
        if (ciphertext.empty()) return "ciphertext ausente";
        if (nonce.empty()) return "nonce ausente";
        if (proof.empty()) return "proof ausente";
        if (timestamp <= 0) return "timestamp invalido";
        if (ttl <= 0 || ttl > kDefaultMaxTtlMs) return "ttl invalido";
        if (now_ms() > timestamp + ttl) return "envelope expirado";
        if (!verify_ed25519(sender_public_key, envelope_canonical_string(envelope), proof)) {
            return "assinatura do envelope invalida";
        }
        return std::nullopt;
    }

    void load() {
        std::lock_guard<std::mutex> lock(mutex_);
        std::ifstream input(storage_file_);
        if (!input.good()) return;
        try {
            json array = json::parse(input);
            if (!array.is_array()) return;
            for (const auto& entry : array) {
                if (!entry.is_object()) continue;
                const auto envelope = entry.contains("envelope") ? entry.at("envelope") : json{};
                const auto id = json_string(envelope, "messageId");
                if (id.empty()) continue;
                EnvelopeRecord record;
                record.envelope = envelope;
                record.received_at = json_i64(entry, "receivedAt", now_ms());
                record.sender_route = json_string(entry, "senderRoute");
                record.recipient_hash = json_string(entry, "recipientHash");
                record.ttl_ms = json_i64(entry, "ttl", json_i64(envelope, "ttl"));
                if (record.recipient_hash.empty()) {
                    record.recipient_hash = json_string(envelope, "recipientPublicKeyHash");
                }
                records_[id] = std::move(record);
            }
            purge_expired_locked();
        } catch (...) {
            std::cerr << "Aviso: nao foi possivel ler " << storage_file_ << "\n";
        }
    }

    void persist_locked() {
        std::filesystem::create_directories(data_dir_);
        json array = json::array();
        std::vector<json> values;
        for (const auto& [_, record] : records_) {
            values.push_back({
                {"messageId", json_string(record.envelope, "messageId")},
                {"receivedAt", record.received_at},
                {"senderRoute", record.sender_route},
                {"recipientHash", record.recipient_hash},
                {"ttl", record.ttl_ms},
                {"envelope", record.envelope}
            });
        }
        std::sort(values.begin(), values.end(), [](const json& a, const json& b) {
            return json_i64(a, "receivedAt") < json_i64(b, "receivedAt");
        });
        for (const auto& envelope : values) array.push_back(envelope);

        const auto temp_file = storage_file_.string() + ".tmp";
        {
            std::ofstream output(temp_file, std::ios::trunc);
            output << array.dump(2);
        }
        std::filesystem::rename(temp_file, storage_file_);
    }

    int purge_expired_locked() {
        const auto before = records_.size();
        const auto current = now_ms();
        for (auto it = records_.begin(); it != records_.end();) {
            const auto timestamp = json_i64(it->second.envelope, "timestamp");
            const auto ttl = json_i64(it->second.envelope, "ttl");
            if (timestamp <= 0 || ttl <= 0 || current > timestamp + ttl) {
                it = records_.erase(it);
            } else {
                ++it;
            }
        }
        const auto removed = static_cast<int>(before - records_.size());
        if (removed > 0) persist_locked();
        return removed;
    }

    struct EnvelopeRecord {
        json envelope;
        long long received_at = 0;
        std::string sender_route;
        std::string recipient_hash;
        long long ttl_ms = 0;
    };

    std::filesystem::path data_dir_;
    std::filesystem::path storage_file_;
    std::mutex mutex_;
    std::unordered_map<std::string, EnvelopeRecord> records_;
};

json parse_json_body(const httplib::Request& request) {
    if (request.body.empty()) return json::object();
    return json::parse(request.body);
}

void respond_json(httplib::Response& response, int status, const json& body) {
    response.status = status;
    response.set_content(body.dump(), "application/json; charset=utf-8");
}

int bounded_limit(const json& request) {
    const auto raw = json_i64(request, "limit", 50);
    return static_cast<int>(std::clamp<long long>(raw, 1, 250));
}

struct RouteEndpoint {
    std::string route;
    std::string host;
    int port = 0;
};

std::optional<RouteEndpoint> parse_route(const std::string& raw) {
    auto clean = trim(raw);
    if (clean.rfind("onion:", 0) == 0) clean = clean.substr(6);
    const auto separator = clean.rfind(':');
    if (separator == std::string::npos || separator == 0 || separator == clean.size() - 1) return std::nullopt;
    auto host = clean.substr(0, separator);
    const auto port_text = clean.substr(separator + 1);
    if (host.size() > 6 && host.substr(host.size() - 6) != ".onion") host += ".onion";
    int port = 0;
    try {
        port = std::stoi(port_text);
    } catch (...) {
        return std::nullopt;
    }
    if (port < 1 || port > 65535) return std::nullopt;
    return RouteEndpoint{"onion:" + host + ":" + std::to_string(port), host, port};
}

std::optional<std::string> detect_local_ipv4() {
    struct ifaddrs* ifaddr = nullptr;
    if (getifaddrs(&ifaddr) != 0) return std::nullopt;
    std::optional<std::string> result;
    for (auto* cursor = ifaddr; cursor != nullptr; cursor = cursor->ifa_next) {
        if (cursor->ifa_addr == nullptr) continue;
        if (cursor->ifa_addr->sa_family != AF_INET) continue;
        if ((cursor->ifa_flags & IFF_LOOPBACK) != 0) continue;
        char buffer[INET_ADDRSTRLEN] = {};
        auto* address = reinterpret_cast<sockaddr_in*>(cursor->ifa_addr);
        if (inet_ntop(AF_INET, &address->sin_addr, buffer, sizeof(buffer)) == nullptr) continue;
        std::string candidate = buffer;
        if (candidate.rfind("10.", 0) == 0 ||
            candidate.rfind("192.168.", 0) == 0 ||
            candidate.rfind("172.16.", 0) == 0 ||
            candidate.rfind("172.17.", 0) == 0 ||
            candidate.rfind("172.18.", 0) == 0 ||
            candidate.rfind("172.19.", 0) == 0 ||
            candidate.rfind("172.2", 0) == 0) {
            result = candidate;
            break;
        }
        if (!result.has_value()) {
            result = candidate;
        }
    }
    freeifaddrs(ifaddr);
    return result;
}

std::string read_relay_url_from_file(const std::string& file_path) {
    if (file_path.empty()) return "";
    std::ifstream input(file_path);
    std::string host;
    std::getline(input, host);
    host = trim(host);
    if (host.empty()) return "";
    if (host.rfind("http://", 0) == 0 || host.rfind("https://", 0) == 0) {
        return trim(host);
    }
    return "http://" + host;
}

std::string relay_announce_url(const std::string& bind_host, int port) {
    const auto explicit_url = trim(env_or("NULLCHAT_RELAY_URL", ""));
    if (!explicit_url.empty()) {
        if (explicit_url.rfind("http://", 0) == 0 || explicit_url.rfind("https://", 0) == 0) {
            return explicit_url;
        }
        return "http://" + explicit_url;
    }

    const auto relay_hostname_file = trim(env_or(
        "NULLCHAT_RELAY_HOSTNAME_FILE",
        env_or("NULLCHAT_NODE_HOSTNAME_FILE", "")
    ));
    const auto onion_url = read_relay_url_from_file(relay_hostname_file);
    if (!onion_url.empty()) {
        return onion_url;
    }

    if (!bind_host.empty() && bind_host != "0.0.0.0" && bind_host != "127.0.0.1") {
        return "http://" + bind_host + ":" + std::to_string(port);
    }
    const auto detected = detect_local_ipv4();
    if (detected && !detected->empty()) {
        return "http://" + *detected + ":" + std::to_string(port);
    }
    return "";
}

bool send_relay_announcement(const std::string& relay_url) {
    if (relay_url.empty()) return false;
    const int fd = ::socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return false;
    int enable = 1;
    setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &enable, sizeof(enable));
    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_port = htons(37020);
    if (::inet_pton(AF_INET, "255.255.255.255", &address.sin_addr) != 1) {
        ::close(fd);
        return false;
    }
    const std::string payload = "NULLCHAT_FAST_RELAY_V1|" + relay_url;
    const bool ok = ::sendto(
        fd,
        payload.data(),
        payload.size(),
        0,
        reinterpret_cast<sockaddr*>(&address),
        sizeof(address)
    ) >= 0;
    ::close(fd);
    return ok;
}

class FastRelayStore {
public:
    explicit FastRelayStore(std::filesystem::path data_dir)
        : data_dir_(std::move(data_dir)), storage_file_(data_dir_ / "fast_relay.json") {
        std::filesystem::create_directories(data_dir_);
        load();
    }

    json stats() {
        std::lock_guard<std::mutex> lock(mutex_);
        purge_expired_locked();
        return {
            {"stored", packets_.size()},
            {"dataFile", storage_file_.string()}
        };
    }

    std::pair<bool, std::string> enqueue(const json& request) {
        const auto route = parse_route(json_string(request, "toRoute"));
        const auto packet = json_string(request, "packet");
        auto ttl = json_i64(request, "ttl", kFastRelayDefaultTtlMs);
        ttl = std::clamp<long long>(ttl, 1'000LL, kFastRelayMaxTtlMs);
        if (!route) return {false, "toRoute invalida"};
        if (packet.empty()) return {false, "packet ausente"};
        if (packet.size() > 192 * 1024) return {false, "packet muito grande"};

        std::lock_guard<std::mutex> lock(mutex_);
        purge_expired_locked();
        packets_.push_back(FastPacket{
            .id = random_id(),
            .to_route = route->route,
            .packet = packet,
            .received_at = now_ms(),
            .ttl_ms = ttl
        });
        persist_locked();
        return {true, "queued"};
    }

    json pull(const std::string& raw_route, int limit) {
        const auto route = parse_route(raw_route);
        if (!route) return json::array();

        std::lock_guard<std::mutex> lock(mutex_);
        purge_expired_locked();
        json result = json::array();
        std::vector<std::string> delivered_ids;
        for (const auto& packet : packets_) {
            if (packet.to_route != route->route) continue;
            if (static_cast<int>(result.size()) >= limit) break;
            result.push_back({
                {"id", packet.id},
                {"packet", packet.packet},
                {"receivedAt", packet.received_at}
            });
            delivered_ids.push_back(packet.id);
        }
        if (!delivered_ids.empty()) {
            packets_.erase(
                std::remove_if(
                    packets_.begin(),
                    packets_.end(),
                    [&](const FastPacket& packet) {
                        return std::find(delivered_ids.begin(), delivered_ids.end(), packet.id) != delivered_ids.end();
                    }
                ),
                packets_.end()
            );
            persist_locked();
        }
        return result;
    }

private:
    void load() {
        std::lock_guard<std::mutex> lock(mutex_);
        std::ifstream input(storage_file_);
        if (!input.good()) return;
        try {
            const auto array = json::parse(input);
            if (!array.is_array()) return;
            for (const auto& item : array) {
                FastPacket packet;
                packet.id = json_string(item, "id");
                packet.to_route = json_string(item, "toRoute");
                packet.packet = json_string(item, "packet");
                packet.received_at = json_i64(item, "receivedAt", now_ms());
                packet.ttl_ms = json_i64(item, "ttl", kFastRelayDefaultTtlMs);
                if (packet.id.empty() || packet.to_route.empty() || packet.packet.empty()) continue;
                packets_.push_back(std::move(packet));
            }
            purge_expired_locked();
        } catch (...) {
            std::cerr << "Aviso: nao foi possivel ler " << storage_file_ << "\n";
        }
    }

    void persist_locked() {
        std::filesystem::create_directories(data_dir_);
        json array = json::array();
        for (const auto& packet : packets_) {
            array.push_back({
                {"id", packet.id},
                {"toRoute", packet.to_route},
                {"packet", packet.packet},
                {"receivedAt", packet.received_at},
                {"ttl", packet.ttl_ms}
            });
        }
        const auto temp_file = storage_file_.string() + ".tmp";
        {
            std::ofstream output(temp_file, std::ios::trunc);
            output << array.dump(2);
        }
        std::filesystem::rename(temp_file, storage_file_);
    }

    int purge_expired_locked() {
        const auto before = packets_.size();
        const auto current = now_ms();
        packets_.erase(
            std::remove_if(
                packets_.begin(),
                packets_.end(),
                [&](const FastPacket& packet) {
                    return packet.received_at <= 0 || packet.ttl_ms <= 0 || current > packet.received_at + packet.ttl_ms;
                }
            ),
            packets_.end()
        );
        const auto removed = static_cast<int>(before - packets_.size());
        if (removed > 0) persist_locked();
        return removed;
    }

    struct FastPacket {
        std::string id;
        std::string to_route;
        std::string packet;
        long long received_at = 0;
        long long ttl_ms = kFastRelayDefaultTtlMs;
    };

    std::filesystem::path data_dir_;
    std::filesystem::path storage_file_;
    std::mutex mutex_;
    std::vector<FastPacket> packets_;
};

std::string route_token(const std::string& route) {
    const auto parsed = parse_route(route);
    if (!parsed) return route;
    auto host = parsed->host;
    if (host.size() > 6 && host.substr(host.size() - 6) == ".onion") {
        host = host.substr(0, host.size() - 6);
    }
    return host + ":" + std::to_string(parsed->port);
}

bool write_all(int fd, const unsigned char* data, size_t size) {
    size_t sent = 0;
    while (sent < size) {
        const auto written = ::send(fd, data + sent, size - sent, 0);
        if (written <= 0) return false;
        sent += static_cast<size_t>(written);
    }
    return true;
}

bool read_exact(int fd, unsigned char* data, size_t size) {
    size_t read_total = 0;
    while (read_total < size) {
        const auto count = ::recv(fd, data + read_total, size - read_total, 0);
        if (count <= 0) return false;
        read_total += static_cast<size_t>(count);
    }
    return true;
}

std::optional<std::string> read_socket_line(int fd, size_t max_chars = 192 * 1024) {
    std::string line;
    line.reserve(512);
    char ch = 0;
    while (line.size() < max_chars) {
        const auto count = ::recv(fd, &ch, 1, 0);
        if (count <= 0) {
            if (line.empty()) return std::nullopt;
            break;
        }
        if (ch == '\n') break;
        if (ch != '\r') line.push_back(ch);
    }
    return line;
}

int connect_socks5(const RouteEndpoint& endpoint, const std::string& socks_host, int socks_port) {
    int fd = ::socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return -1;

    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_port = htons(static_cast<uint16_t>(socks_port));
    if (::inet_pton(AF_INET, socks_host.c_str(), &address.sin_addr) != 1) {
        ::close(fd);
        return -1;
    }
    if (::connect(fd, reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0) {
        ::close(fd);
        return -1;
    }

    const unsigned char greeting[] = {0x05, 0x01, 0x00};
    unsigned char greeting_response[2]{};
    if (!write_all(fd, greeting, sizeof(greeting)) || !read_exact(fd, greeting_response, sizeof(greeting_response)) ||
        greeting_response[0] != 0x05 || greeting_response[1] != 0x00) {
        ::close(fd);
        return -1;
    }

    std::vector<unsigned char> request;
    request.push_back(0x05);
    request.push_back(0x01);
    request.push_back(0x00);
    request.push_back(0x03);
    if (endpoint.host.size() > 255) {
        ::close(fd);
        return -1;
    }
    request.push_back(static_cast<unsigned char>(endpoint.host.size()));
    request.insert(request.end(), endpoint.host.begin(), endpoint.host.end());
    request.push_back(static_cast<unsigned char>((endpoint.port >> 8) & 0xFF));
    request.push_back(static_cast<unsigned char>(endpoint.port & 0xFF));
    unsigned char response_header[4]{};
    if (!write_all(fd, request.data(), request.size()) || !read_exact(fd, response_header, sizeof(response_header)) ||
        response_header[0] != 0x05 || response_header[1] != 0x00) {
        ::close(fd);
        return -1;
    }
    size_t tail = 0;
    if (response_header[3] == 0x01) tail = 4 + 2;
    else if (response_header[3] == 0x03) {
        unsigned char len = 0;
        if (!read_exact(fd, &len, 1)) {
            ::close(fd);
            return -1;
        }
        tail = len + 2;
    } else if (response_header[3] == 0x04) tail = 16 + 2;
    else {
        ::close(fd);
        return -1;
    }
    std::vector<unsigned char> discard(tail);
    if (!read_exact(fd, discard.data(), discard.size())) {
        ::close(fd);
        return -1;
    }
    return fd;
}

class CliNode {
public:
    CliNode(
        std::string route,
        std::string socks_host,
        int socks_port,
        int chat_port,
        std::filesystem::path data_dir,
        bool enable_cli
    )
        : local_route_(std::move(route)),
          socks_host_(std::move(socks_host)),
          socks_port_(socks_port),
          chat_port_(chat_port),
          data_dir_(std::move(data_dir)),
          contacts_file_(data_dir_ / "contacts.json"),
          enable_cli_(enable_cli) {
        std::filesystem::create_directories(data_dir_);
        load_contacts();
    }

    void start() {
        server_thread_ = std::thread([this] { run_server(); });
        if (enable_cli_) {
            cli_thread_ = std::thread([this] { run_cli(); });
        }
    }

    void stop() {
        stopped_ = true;
        g_running = false;
        if (server_fd_ >= 0) {
            ::shutdown(server_fd_, SHUT_RDWR);
            ::close(server_fd_);
            server_fd_ = -1;
        }
    }

    void join() {
        if (cli_thread_.joinable()) cli_thread_.join();
        stop();
        if (server_thread_.joinable()) server_thread_.join();
    }

private:
    void run_server() {
        server_fd_ = ::socket(AF_INET, SOCK_STREAM, 0);
        if (server_fd_ < 0) {
            print("falha ao criar socket do chat");
            return;
        }
        int enabled = 1;
        setsockopt(server_fd_, SOL_SOCKET, SO_REUSEADDR, &enabled, sizeof(enabled));
        sockaddr_in address{};
        address.sin_family = AF_INET;
        address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        address.sin_port = htons(static_cast<uint16_t>(chat_port_));
        if (::bind(server_fd_, reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0 ||
            ::listen(server_fd_, 50) != 0) {
            print("falha ao abrir chat em 127.0.0.1:" + std::to_string(chat_port_));
            return;
        }
        print("chat ouvindo em 127.0.0.1:" + std::to_string(chat_port_));
        while (!stopped_ && g_running) {
            const int client = ::accept(server_fd_, nullptr, nullptr);
            if (client < 0) {
                if (!stopped_) std::this_thread::sleep_for(std::chrono::milliseconds(150));
                continue;
            }
            std::thread([this, client] { handle_client(client); }).detach();
        }
    }

    void handle_client(int fd) {
        while (!stopped_ && g_running) {
            const auto line = read_socket_line(fd);
            if (!line) break;
            handle_encrypted_line(*line);
        }
        ::close(fd);
    }

    void handle_encrypted_line(const std::string& line) {
        const auto plain = simple_decrypt(line);
        if (!plain) return;
        const auto separator = plain->find('|');
        if (separator == std::string::npos || separator == 0) return;
        const auto from = parse_route(plain->substr(0, separator));
        if (!from) return;
        const auto text = plain->substr(separator + 1);

        if (text.rfind(kChatPresencePrefix, 0) == 0) {
            mark_seen(from->route);
            return;
        }
        if (text.rfind(kChatAckPrefix, 0) == 0) {
            print("ack de " + route_token(from->route) + ": " + trim(text.substr(std::strlen(kChatAckPrefix))));
            mark_seen(from->route);
            return;
        }
        if (text == kContactAccept) {
            {
                std::lock_guard<std::mutex> lock(mutex_);
                accepted_.insert(from->route);
                pending_.erase(from->route);
                active_peer_ = from->route;
                persist_contacts_locked();
            }
            print(route_token(from->route) + " aceitou o contato. Chat ativo.");
            return;
        }
        if (text == kContactRequest) {
            handle_contact_request(from->route);
            return;
        }

        std::string message_id;
        const auto message = decode_chat_message(text, &message_id);
        if (!is_accepted(from->route)) {
            print("mensagem bloqueada de contato nao aceito: " + route_token(from->route));
            return;
        }
        mark_seen(from->route);
        {
            std::lock_guard<std::mutex> lock(mutex_);
            active_peer_ = from->route;
        }
        print("\n[" + route_token(from->route) + "] " + message);
        if (!message_id.empty()) {
            send_raw(from->route, std::string(kChatAckPrefix) + message_id);
        }
    }

    void handle_contact_request(const std::string& route) {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            pending_.insert(route);
            prompt_peer_ = route;
            persist_contacts_locked();
        }
        print("\nPedido de contato de " + route_token(route) + ". Aceitar? [s/N]");
    }

    std::string decode_chat_message(const std::string& text, std::string* message_id) {
        if (text.rfind(kChatMessagePrefix, 0) != 0) return text;
        const auto payload = text.substr(std::strlen(kChatMessagePrefix));
        const auto first = payload.find('|');
        if (first == std::string::npos) return text;
        *message_id = trim(payload.substr(0, first));
        const auto remaining = payload.substr(first + 1);
        const auto second = remaining.find('|');
        const auto encoded = second == std::string::npos ? remaining : remaining.substr(second + 1);
        return base64_decode_to_string(encoded).value_or(text);
    }

    void run_cli() {
        print("NullChat node CLI pronto. /help mostra comandos.");
        if (local_route_.empty()) {
            print("aviso: NULLCHAT_NODE_ROUTE nao definido; o app mobile rejeita mensagens sem rota onion local.");
        } else {
            print("rota local: " + local_route_);
        }
        std::string line;
        while (g_running && std::getline(std::cin, line)) {
            const auto clean = trim(line);
            if (clean.empty()) continue;
            const auto prompted = prompted_peer();
            if (!prompted.empty() && (clean == "s" || clean == "S" || clean == "sim" || clean == "SIM" || clean == "Sim")) {
                clear_prompt();
                accept(prompted);
                continue;
            }
            if (!prompted.empty() && (clean == "n" || clean == "N" || clean == "nao" || clean == "não")) {
                clear_prompt();
                print("pedido mantido pendente. Use /accept " + route_token(prompted));
                continue;
            }
            if (clean == "/quit" || clean == "/exit") {
                stop();
                break;
            }
            if (clean == "/help") {
                print_help();
                continue;
            }
            if (clean == "/contacts") {
                print_contacts();
                continue;
            }
            if (clean == "/route") {
                print(local_route_.empty() ? "rota local nao configurada" : local_route_);
                continue;
            }
            if (clean.rfind("/to ", 0) == 0) {
                set_active(clean.substr(4));
                continue;
            }
            if (clean.rfind("/add ", 0) == 0) {
                add_contact(clean.substr(5));
                continue;
            }
            if (clean.rfind("/accept ", 0) == 0) {
                accept(clean.substr(8));
                continue;
            }
            const auto target = active_peer();
            if (target.empty()) {
                print("sem chat ativo. Use /to <rota> ou /add <rota>.");
                continue;
            }
            if (!is_accepted(target)) {
                print("contato ainda nao aceito. Use /add " + route_token(target) + " ou aguarde aceite.");
                continue;
            }
            send_chat(target, clean);
        }
        stop();
    }

    void print_help() {
        print("comandos:");
        print("  /route                 mostra a rota local configurada");
        print("  /add <rota>            envia pedido de contato");
        print("  /to <rota>             troca o chat ativo");
        print("  /accept <rota>         aceita contato pendente");
        print("  /contacts              lista contatos");
        print("  /quit                  encerra");
        print("texto sem / envia mensagem para o chat ativo");
    }

    void print_contacts() {
        std::lock_guard<std::mutex> lock(mutex_);
        if (accepted_.empty() && pending_.empty()) {
            print("nenhum contato ainda");
            return;
        }
        for (const auto& route : accepted_) print("aceito: " + route_token(route));
        for (const auto& route : pending_) print("pendente: " + route_token(route));
    }

    void set_active(const std::string& raw_route) {
        const auto route = resolve_route(raw_route);
        if (!route) {
            print("rota invalida");
            return;
        }
        std::lock_guard<std::mutex> lock(mutex_);
        active_peer_ = route->route;
        persist_contacts_locked();
        print("chat ativo: " + route_token(route->route));
    }

    void add_contact(const std::string& raw_route) {
        const auto route = resolve_route(raw_route);
        if (!route) {
            print("rota invalida");
            return;
        }
        {
            std::lock_guard<std::mutex> lock(mutex_);
            active_peer_ = route->route;
            persist_contacts_locked();
        }
        if (send_raw(route->route, kContactRequest)) {
            print("pedido enviado para " + route_token(route->route));
        }
    }

    void accept(const std::string& raw_route) {
        const auto route = resolve_route(raw_route);
        if (!route) {
            print("rota invalida");
            return;
        }
        {
            std::lock_guard<std::mutex> lock(mutex_);
            accepted_.insert(route->route);
            pending_.erase(route->route);
            active_peer_ = route->route;
            persist_contacts_locked();
        }
        send_raw(route->route, kContactAccept);
        send_raw(route->route, std::string(kChatPresencePrefix) + "open");
        print("contato aceito: " + route_token(route->route));
    }

    void send_chat(const std::string& route, const std::string& text) {
        const auto id = random_id();
        const auto payload = std::string(kChatMessagePrefix) + id + "|" + std::to_string(now_ms()) + "|" + base64_encode(text);
        if (send_raw(route, payload)) {
            print("[voce -> " + route_token(route) + "] " + text);
        }
    }

    bool send_raw(const std::string& raw_route, const std::string& text) {
        const auto route = resolve_route(raw_route);
        if (!route) {
            print("rota invalida: " + raw_route);
            return false;
        }
        if (local_route_.empty()) {
            print("NULLCHAT_NODE_ROUTE nao configurado; nao da para enviar.");
            return false;
        }
        const int fd = connect_socks5(*route, socks_host_, socks_port_);
        if (fd < 0) {
            print("falha ao conectar via SOCKS em " + socks_host_ + ":" + std::to_string(socks_port_));
            return false;
        }
        const auto encrypted = simple_encrypt(local_route_ + "|" + text);
        if (!encrypted) {
            ::close(fd);
            print("falha ao criptografar mensagem");
            return false;
        }
        const auto line = *encrypted + "\n";
        const bool ok = write_all(fd, reinterpret_cast<const unsigned char*>(line.data()), line.size());
        ::close(fd);
        if (!ok) print("falha ao enviar para " + route_token(route->route));
        return ok;
    }

    std::optional<RouteEndpoint> resolve_route(const std::string& raw_route) {
        auto parsed = parse_route(raw_route);
        if (parsed) return parsed;
        const auto token = trim(raw_route);
        std::lock_guard<std::mutex> lock(mutex_);
        for (const auto& route : accepted_) {
            if (route_token(route) == token) return parse_route(route);
        }
        for (const auto& route : pending_) {
            if (route_token(route) == token) return parse_route(route);
        }
        return std::nullopt;
    }

    void mark_seen(const std::string& route) {
        std::lock_guard<std::mutex> lock(mutex_);
        seen_[route] = now_ms();
    }

    bool is_accepted(const std::string& route) {
        std::lock_guard<std::mutex> lock(mutex_);
        return accepted_.find(route) != accepted_.end();
    }

    void load_contacts() {
        std::lock_guard<std::mutex> lock(mutex_);
        std::ifstream input(contacts_file_);
        if (!input.good()) return;
        try {
            const auto root = json::parse(input);
            if (root.contains("accepted") && root["accepted"].is_array()) {
                for (const auto& route : root["accepted"]) {
                    if (route.is_string()) accepted_.insert(trim(route.get<std::string>()));
                }
            }
            if (root.contains("pending") && root["pending"].is_array()) {
                for (const auto& route : root["pending"]) {
                    if (route.is_string()) pending_.insert(trim(route.get<std::string>()));
                }
            }
            active_peer_ = trim(root.value("activePeer", ""));
            prompt_peer_ = trim(root.value("promptPeer", ""));
        } catch (...) {
            print("aviso: nao foi possivel ler " + contacts_file_.string());
        }
    }

    void persist_contacts_locked() {
        json root = {
            {"accepted", json::array()},
            {"pending", json::array()},
            {"activePeer", active_peer_},
            {"promptPeer", prompt_peer_}
        };
        for (const auto& route : accepted_) root["accepted"].push_back(route);
        for (const auto& route : pending_) root["pending"].push_back(route);
        std::filesystem::create_directories(data_dir_);
        const auto temp_file = contacts_file_.string() + ".tmp";
        {
            std::ofstream output(temp_file, std::ios::trunc);
            output << root.dump(2);
        }
        std::filesystem::rename(temp_file, contacts_file_);
    }

    std::string active_peer() {
        std::lock_guard<std::mutex> lock(mutex_);
        return active_peer_;
    }

    std::string prompted_peer() {
        std::lock_guard<std::mutex> lock(mutex_);
        return prompt_peer_;
    }

    void clear_prompt() {
        std::lock_guard<std::mutex> lock(mutex_);
        prompt_peer_.clear();
    }

    void print(const std::string& message) {
        std::lock_guard<std::mutex> lock(output_mutex_);
        std::cout << message << std::endl;
    }

    std::string local_route_;
    std::string socks_host_;
    int socks_port_;
    int chat_port_;
    std::filesystem::path data_dir_;
    std::filesystem::path contacts_file_;
    std::atomic_bool stopped_{false};
    int server_fd_ = -1;
    std::thread server_thread_;
    std::thread cli_thread_;
    bool enable_cli_ = true;
    std::mutex mutex_;
    std::mutex output_mutex_;
    std::unordered_set<std::string> accepted_;
    std::unordered_set<std::string> pending_;
    std::unordered_map<std::string, long long> seen_;
    std::string active_peer_;
    std::string prompt_peer_;
};

std::string read_local_route_from_env() {
    auto route = trim(env_or("NULLCHAT_NODE_ROUTE", ""));
    if (!route.empty()) {
        const auto parsed = parse_route(route);
        return parsed ? parsed->route : "";
    }
    const auto hostname_file = trim(env_or("NULLCHAT_NODE_HOSTNAME_FILE", ""));
    if (!hostname_file.empty()) {
        std::ifstream input(hostname_file);
        std::string host;
        std::getline(input, host);
        host = trim(host);
        if (!host.empty()) {
            const auto parsed = parse_route("onion:" + host + ":" + std::to_string(kChatProtocolPort));
            return parsed ? parsed->route : "";
        }
    }
    return "";
}
}

int main() {
    const auto host = env_or("NULLCHAT_NODE_HOST", "127.0.0.1");
    const auto port = env_int_or("NULLCHAT_NODE_PORT", 5080);
    const auto data_dir = env_or("NULLCHAT_NODE_DATA", "node/data");
    const auto socks_host = env_or("NULLCHAT_SOCKS_HOST", "127.0.0.1");
    const auto socks_port = env_int_or("NULLCHAT_SOCKS_PORT", 9050);
    const auto chat_port = env_int_or("NULLCHAT_CHAT_PORT", kChatProtocolPort);
    const auto local_route = read_local_route_from_env();
    const auto cli_env = trim(env_or("NULLCHAT_NODE_CLI", ""));
    const bool enable_cli = cli_env.empty() ? ::isatty(STDIN_FILENO) != 0 : (cli_env != "0" && cli_env != "false" && cli_env != "FALSE");

    EnvelopeStore store(data_dir);
    FastRelayStore fast_relay_store(std::filesystem::path(data_dir) / "fast_relay");
    RelayAuthStore relay_auth_store;
    httplib::Server server;
    server.set_payload_max_length(256 * 1024);
    std::signal(SIGINT, [](int) { g_running = false; });
    std::signal(SIGTERM, [](int) { g_running = false; });

    server.Get("/health", [&](const httplib::Request&, httplib::Response& response) {
        respond_json(response, 200, {
            {"ok", true},
            {"role", "nullchat_node"},
            {"protocol", kProtocolPrefix},
            {"chatPort", chat_port},
            {"route", local_route},
            {"store", store.stats()},
            {"fastRelay", fast_relay_store.stats()}
        });
    });

    server.Post("/v1/fast/send", [&](const httplib::Request& request, httplib::Response& response) {
        try {
            if (!relay_auth_store.validate(request, request.body)) {
                respond_json(response, 401, {{"ok", false}, {"error", "assinatura invalida"}});
                return;
            }
            const auto body = parse_json_body(request);
            const auto [ok, message] = fast_relay_store.enqueue(body);
            respond_json(response, ok ? 200 : 400, {{"ok", ok}, {"message", message}});
        } catch (const std::exception& error) {
            respond_json(response, 400, {{"ok", false}, {"error", error.what()}});
        }
    });

    server.Post("/v1/fast/pull", [&](const httplib::Request& request, httplib::Response& response) {
        try {
            if (!relay_auth_store.validate(request, request.body)) {
                respond_json(response, 401, {{"ok", false}, {"error", "assinatura invalida"}});
                return;
            }
            const auto body = parse_json_body(request);
            const auto route = json_string(body, "route");
            if (!parse_route(route)) {
                respond_json(response, 400, {{"ok", false}, {"error", "route invalida"}});
                return;
            }
            respond_json(response, 200, {
                {"ok", true},
                {"packets", fast_relay_store.pull(route, bounded_limit(body))}
            });
        } catch (const std::exception& error) {
            respond_json(response, 400, {{"ok", false}, {"error", error.what()}});
        }
    });

    server.Post("/v1/envelopes", [&](const httplib::Request& request, httplib::Response& response) {
        try {
            const auto envelope = parse_json_body(request);
            const auto [ok, message] = store.upsert(envelope);
            respond_json(response, ok ? 200 : 400, {{"ok", ok}, {"message", message}});
        } catch (const std::exception& error) {
            respond_json(response, 400, {{"ok", false}, {"error", error.what()}});
        }
    });

    server.Post("/v1/pull", [&](const httplib::Request& request, httplib::Response& response) {
        try {
            const auto body = parse_json_body(request);
            const auto recipient_hash = json_string(body, "recipientPublicKeyHash");
            if (recipient_hash.empty()) {
                respond_json(response, 400, {{"ok", false}, {"error", "recipientPublicKeyHash ausente"}});
                return;
            }
            respond_json(response, 200, {
                {"ok", true},
                {"envelopes", store.pull(recipient_hash, json_i64(body, "sinceTimestamp"), bounded_limit(body))}
            });
        } catch (const std::exception& error) {
            respond_json(response, 400, {{"ok", false}, {"error", error.what()}});
        }
    });

    server.Post("/v1/ack", [&](const httplib::Request& request, httplib::Response& response) {
        try {
            const auto ack = parse_json_body(request);
            const auto [ok, message] = store.ack(ack);
            respond_json(response, ok ? 200 : 400, {{"ok", ok}, {"message", message}});
        } catch (const std::exception& error) {
            respond_json(response, 400, {{"ok", false}, {"error", error.what()}});
        }
    });

    std::atomic_bool relay_announcement_running{true};
    std::thread relay_announcement_thread([&] {
        while (g_running && relay_announcement_running) {
            const auto relay_announcement_url = relay_announce_url(host, port);
            if (!relay_announcement_url.empty()) {
                send_relay_announcement(relay_announcement_url);
            }
            std::this_thread::sleep_for(std::chrono::seconds(3));
        }
    });

    server.Post("/v1/p2p", [&](const httplib::Request& request, httplib::Response& response) {
        const auto packet = decode_wire_packet(trim(request.body));
        if (!packet) {
            respond_json(response, 400, {{"ok", false}, {"error", "pacote P2P_V1 invalido"}});
            return;
        }

        if (packet->type == kTypeEnvelope) {
            const auto [ok, message] = store.upsert(packet->payload);
            respond_json(response, ok ? 200 : 400, {{"ok", ok}, {"message", message}});
            return;
        }

        if (packet->type == kTypePull) {
            const auto recipient_hash = json_string(packet->payload, "recipientPublicKeyHash");
            if (recipient_hash.empty()) {
                respond_json(response, 400, {{"ok", false}, {"error", "recipientPublicKeyHash ausente"}});
                return;
            }
            const auto envelopes = store.pull(
                recipient_hash,
                json_i64(packet->payload, "sinceTimestamp"),
                bounded_limit(packet->payload)
            );
            response.status = 200;
            response.set_content(encode_wire_packet(kTypePullResponse, envelopes), "text/plain; charset=utf-8");
            return;
        }

        if (packet->type == kTypeAck) {
            const auto [ok, message] = store.ack(packet->payload);
            respond_json(response, ok ? 200 : 400, {{"ok", ok}, {"message", message}});
            return;
        }

        respond_json(response, 400, {{"ok", false}, {"error", "tipo P2P_V1 nao suportado"}});
    });

    CliNode cli(local_route, socks_host, socks_port, chat_port, data_dir, enable_cli);
    cli.start();

    std::thread http_thread([&] {
        std::cout << "NullChat node HTTP ouvindo em http://" << host << ":" << port << "\n";
        std::cout << "Dados: " << data_dir << "\n";
        if (!server.listen(host, port)) {
            std::cerr << "Falha ao iniciar servidor em " << host << ":" << port << "\n";
            g_running = false;
            cli.stop();
        }
    });

    while (g_running) {
        std::this_thread::sleep_for(std::chrono::milliseconds(250));
    }
    cli.stop();
    relay_announcement_running = false;
    if (relay_announcement_thread.joinable()) relay_announcement_thread.join();
    server.stop();
    cli.join();
    if (http_thread.joinable()) http_thread.join();
    return 0;
}
