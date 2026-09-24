#pragma once
#include <cstdint>
#include <map>
#include <mutex>
#include <string>
#include <vector>

namespace nullmarket {
using Amount = std::int64_t;
enum class Side { Buy, Sell };
struct Balance { Amount available = 0; Amount reserved = 0; };
struct Posting { std::string account; std::string asset; Amount delta = 0; };
struct Order {
    std::uint64_t id = 0, sequence = 0;
    std::string user, base, quote;
    Side side = Side::Buy;
    Amount price = 0, quantity = 0, remaining = 0;
};
struct Trade {
    std::uint64_t maker_order = 0, taker_order = 0;
    Amount price = 0, quantity = 0, taker_fee_quote = 0;
};

class Ledger {
public:
    explicit Ledger(std::string journal_path);
    Balance balance(const std::string&, const std::string&) const;
    void post(const std::string&, const std::vector<Posting>&);
    void reserve(const std::string&, const std::string&, Amount);
    void release(const std::string&, const std::string&, Amount);
    void settle_reserved(const std::string&, const std::string&, const std::string&,
                         Amount, const std::vector<Posting>&);
private:
    std::string journal_path_;
    mutable std::mutex mutex_;
    std::map<std::pair<std::string, std::string>, Balance> balances_;
    void append(const std::string&) const;
};

class MatchingEngine {
public:
    MatchingEngine(Ledger&, Amount price_scale, std::uint32_t taker_fee_ppm);
    std::pair<Order, std::vector<Trade>> place(const std::string&, const std::string&,
        const std::string&, Side, Amount, Amount);
    bool cancel(const std::string&, std::uint64_t);
    std::vector<Order> open_orders() const;
private:
    Ledger& ledger_;
    Amount scale_;
    std::uint32_t fee_ppm_;
    mutable std::mutex mutex_;
    std::uint64_t next_id_ = 1, next_sequence_ = 1;
    std::vector<Order> orders_;
    Amount quote_value(Amount, Amount) const;
    Amount fee(Amount) const;
};
}
