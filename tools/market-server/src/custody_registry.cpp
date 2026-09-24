#include "custody_registry.hpp"

#include <algorithm>
#include <cctype>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <stdexcept>
#include <vector>

namespace nullmarket {
namespace {
std::string normalize_identity_hash(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    if (value.size() != 64 || !std::all_of(value.begin(), value.end(), [](unsigned char c) {
        return std::isdigit(c) || (c >= 'a' && c <= 'f');
    })) throw std::invalid_argument("identity hash must be 64 hexadecimal characters");
    return value;
}

bool valid_token(const std::string& value, std::size_t maximum) {
    return !value.empty() && value.size() <= maximum &&
        std::all_of(value.begin(), value.end(), [](unsigned char c) {
            return std::isalnum(c) || c == ':' || c == '_' || c == '-';
        });
}

std::vector<std::string> split_tabs(const std::string& line) {
    std::vector<std::string> fields;
    std::istringstream input(line);
    std::string field;
    while (std::getline(input, field, '\t')) fields.push_back(field);
    return fields;
}
}

CustodyRegistry::CustodyRegistry(std::string path) : journal_path_(std::move(path)) { load(); }

void CustodyRegistry::append(const std::string& line) const {
    const std::filesystem::path path(journal_path_);
    if (path.has_parent_path()) std::filesystem::create_directories(path.parent_path());
    std::ofstream output(path, std::ios::app);
    if (!output) throw std::runtime_error("custody journal unavailable");
    output << line << '\n';
    output.flush();
    if (!output) throw std::runtime_error("custody journal write failed");
}

void CustodyRegistry::load() {
    std::ifstream input(journal_path_);
    if (!input) return;
    std::string line;
    while (std::getline(input, line)) {
        const auto fields = split_tabs(line);
        if (fields.size() == 2 && fields[0] == "ACCOUNT") {
            const auto identity = normalize_identity_hash(fields[1]);
            accounts_[identity] = {"acct_" + identity, identity, {}};
        } else if (fields.size() == 4 && fields[0] == "ADDRESS") {
            const auto identity = normalize_identity_hash(fields[1]);
            if (!valid_token(fields[2], 16) || !valid_token(fields[3], 160)) {
                throw std::runtime_error("invalid custody journal record");
            }
            auto& account = accounts_[identity];
            account.account_id = "acct_" + identity;
            account.identity_hash = identity;
            account.deposit_addresses[fields[2]] = fields[3];
        } else if (!line.empty()) {
            throw std::runtime_error("invalid custody journal");
        }
    }
}

CustodyAccount CustodyRegistry::account_for_identity(const std::string& raw_identity) {
    const auto identity = normalize_identity_hash(raw_identity);
    std::lock_guard<std::mutex> lock(mutex_);
    const auto found = accounts_.find(identity);
    if (found != accounts_.end()) return found->second;
    CustodyAccount account{"acct_" + identity, identity, {}};
    append("ACCOUNT\t" + identity);
    accounts_[identity] = account;
    return account;
}

void CustodyRegistry::register_deposit_address(const std::string& raw_identity,
                                               const std::string& asset,
                                               const std::string& address) {
    const auto identity = normalize_identity_hash(raw_identity);
    if (!valid_token(asset, 16) || !valid_token(address, 160)) throw std::invalid_argument("invalid deposit address");
    std::lock_guard<std::mutex> lock(mutex_);
    auto& account = accounts_[identity];
    if (account.account_id.empty()) {
        account = {"acct_" + identity, identity, {}};
        append("ACCOUNT\t" + identity);
    }
    const auto existing = account.deposit_addresses.find(asset);
    if (existing != account.deposit_addresses.end()) {
        if (existing->second != address) throw std::runtime_error("deposit address already registered");
        return;
    }
    append("ADDRESS\t" + identity + "\t" + asset + "\t" + address);
    account.deposit_addresses[asset] = address;
}

CustodyAccount CustodyRegistry::get(const std::string& raw_identity) const {
    const auto identity = normalize_identity_hash(raw_identity);
    std::lock_guard<std::mutex> lock(mutex_);
    const auto found = accounts_.find(identity);
    if (found == accounts_.end()) throw std::runtime_error("custody account not found");
    return found->second;
}

} // namespace nullmarket
