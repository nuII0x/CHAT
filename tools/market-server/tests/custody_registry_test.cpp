#include "custody_registry.hpp"

#include <cassert>
#include <filesystem>
#include <iostream>

using namespace nullmarket;

int main() {
    const auto journal = std::filesystem::temp_directory_path() / "null-market-custody-test.journal";
    std::filesystem::remove(journal);
    const std::string identity(64, 'a');
    std::string account_id;
    {
        CustodyRegistry registry(journal.string());
        const auto account = registry.account_for_identity(identity);
        account_id = account.account_id;
        registry.register_deposit_address(identity, "BTC", "bcrt1qexampletestaddress");
        registry.register_deposit_address(identity, "XMR", "44AFFq5kExampleStagenetAddress");
    }
    {
        CustodyRegistry restored(journal.string());
        const auto account = restored.account_for_identity(identity);
        assert(account.account_id == account_id);
        assert(account.deposit_addresses.at("BTC") == "bcrt1qexampletestaddress");
        assert(account.deposit_addresses.at("XMR") == "44AFFq5kExampleStagenetAddress");
    }
    bool rejected = false;
    try { CustodyRegistry(journal.string()).account_for_identity("not-a-hash"); }
    catch (...) { rejected = true; }
    assert(rejected);
    std::filesystem::remove(journal);
    std::cout << "custody registry tests passed\n";
}
