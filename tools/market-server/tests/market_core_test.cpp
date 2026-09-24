#include "market_core.hpp"
#include <cassert>
#include <filesystem>
#include <iostream>
using namespace nullmarket;
int main() {
    const auto path = std::filesystem::temp_directory_path() / "null-market-core-test.journal";
    std::filesystem::remove(path); Ledger ledger(path.string());
    ledger.post("deposit-buyer", {{"buyer", "USDT", 2'000'000}, {"external", "USDT", -2'000'000}});
    ledger.post("deposit-seller", {{"seller", "BTC", 100'000'000}, {"external", "BTC", -100'000'000}});
    MatchingEngine engine(ledger, 100'000'000, 2'000);
    const auto sell = engine.place("seller", "BTC", "USDT", Side::Sell, 1'000'000, 50'000'000);
    assert(sell.second.empty() && ledger.balance("seller", "BTC").reserved == 50'000'000);
    const auto buy = engine.place("buyer", "BTC", "USDT", Side::Buy, 1'000'000, 20'000'000);
    assert(buy.second.size() == 1 && ledger.balance("buyer", "BTC").available == 20'000'000);
    assert(ledger.balance("seller", "USDT").available == 200'000);
    assert(ledger.balance("platform_revenue", "USDT").available == 400);
    assert(engine.cancel("seller", sell.first.id) && ledger.balance("seller", "BTC").reserved == 0);
    bool rejected = false;
    try { ledger.post("bad", {{"buyer", "BTC", 1}, {"external", "BTC", -2}}); } catch (...) { rejected = true; }
    assert(rejected); std::filesystem::remove(path); std::cout << "market core tests passed\n";
}
