#pragma once

#include <map>
#include <mutex>
#include <string>

namespace nullmarket {

struct CustodyAccount {
    std::string account_id;
    std::string identity_hash;
    std::map<std::string, std::string> deposit_addresses;
};

class CustodyRegistry {
public:
    explicit CustodyRegistry(std::string journal_path);
    CustodyAccount account_for_identity(const std::string& identity_hash);
    void register_deposit_address(const std::string& identity_hash,
                                  const std::string& asset,
                                  const std::string& address);
    CustodyAccount get(const std::string& identity_hash) const;

private:
    std::string journal_path_;
    mutable std::mutex mutex_;
    std::map<std::string, CustodyAccount> accounts_;
    void load();
    void append(const std::string& line) const;
};

} // namespace nullmarket
