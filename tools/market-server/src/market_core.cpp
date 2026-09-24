#include "market_core.hpp"
#include <algorithm>
#include <filesystem>
#include <fstream>
#include <limits>
#include <sstream>
#include <stdexcept>

namespace nullmarket {
namespace {
constexpr Amount ppm_scale = 1'000'000;
Amount add(Amount a, Amount b) {
    if ((b > 0 && a > std::numeric_limits<Amount>::max() - b) ||
        (b < 0 && a < std::numeric_limits<Amount>::min() - b)) throw std::overflow_error("amount overflow");
    return a + b;
}
}
Ledger::Ledger(std::string path) : journal_path_(std::move(path)) {}
Balance Ledger::balance(const std::string& account, const std::string& asset) const {
    std::lock_guard<std::mutex> lock(mutex_);
    const auto found = balances_.find({account, asset});
    return found == balances_.end() ? Balance{} : found->second;
}
void Ledger::append(const std::string& line) const {
    const std::filesystem::path path(journal_path_);
    if (path.has_parent_path()) std::filesystem::create_directories(path.parent_path());
    std::ofstream out(path, std::ios::app);
    if (!out) throw std::runtime_error("journal unavailable");
    out << line << '\n'; out.flush();
    if (!out) throw std::runtime_error("journal write failed");
}
void Ledger::post(const std::string& id, const std::vector<Posting>& postings) {
    if (id.empty() || postings.size() < 2) throw std::invalid_argument("invalid transaction");
    std::map<std::string, Amount> totals;
    for (const auto& p : postings) {
        if (p.account.empty() || p.asset.empty() || p.delta == 0) throw std::invalid_argument("invalid posting");
        totals[p.asset] = add(totals[p.asset], p.delta);
    }
    for (const auto& total : totals) if (total.second != 0) throw std::invalid_argument("unbalanced transaction");
    std::lock_guard<std::mutex> lock(mutex_);
    auto next = balances_; std::ostringstream line; line << id;
    for (const auto& p : postings) {
        auto& b = next[{p.account, p.asset}]; b.available = add(b.available, p.delta);
        if (p.account != "external" && b.available < 0) throw std::runtime_error("insufficient balance");
        line << '\t' << p.account << '\t' << p.asset << '\t' << p.delta;
    }
    append(line.str()); balances_.swap(next);
}
void Ledger::reserve(const std::string& account, const std::string& asset, Amount amount) {
    if (amount <= 0) throw std::invalid_argument("invalid reserve");
    std::lock_guard<std::mutex> lock(mutex_); auto& b = balances_[{account, asset}];
    if (b.available < amount) throw std::runtime_error("insufficient balance");
    b.available -= amount; b.reserved = add(b.reserved, amount);
}
void Ledger::release(const std::string& account, const std::string& asset, Amount amount) {
    if (amount <= 0) throw std::invalid_argument("invalid release");
    std::lock_guard<std::mutex> lock(mutex_); auto& b = balances_[{account, asset}];
    if (b.reserved < amount) throw std::runtime_error("insufficient reserve");
    b.reserved -= amount; b.available = add(b.available, amount);
}
void Ledger::settle_reserved(const std::string& id, const std::string& source,
    const std::string& asset, Amount debit, const std::vector<Posting>& credits) {
    if (debit <= 0 || credits.empty()) throw std::invalid_argument("invalid settlement");
    Amount total = 0;
    for (const auto& p : credits) {
        if (p.asset != asset || p.delta <= 0) throw std::invalid_argument("invalid credit");
        total = add(total, p.delta);
    }
    if (total != debit) throw std::invalid_argument("unbalanced settlement");
    std::lock_guard<std::mutex> lock(mutex_); auto next = balances_; auto& from = next[{source, asset}];
    if (from.reserved < debit) throw std::runtime_error("insufficient reserve");
    from.reserved -= debit; std::ostringstream line; line << id << '\t' << source << '\t' << asset << '\t' << -debit;
    for (const auto& p : credits) { next[{p.account, asset}].available = add(next[{p.account, asset}].available, p.delta); line << '\t' << p.account << '\t' << asset << '\t' << p.delta; }
    append(line.str()); balances_.swap(next);
}
MatchingEngine::MatchingEngine(Ledger& ledger, Amount scale, std::uint32_t fee_ppm)
    : ledger_(ledger), scale_(scale), fee_ppm_(fee_ppm) {
    if (scale <= 0 || fee_ppm >= ppm_scale) throw std::invalid_argument("invalid config");
}
Amount MatchingEngine::quote_value(Amount price, Amount quantity) const {
    if (price <= 0 || quantity <= 0 || quantity > std::numeric_limits<Amount>::max() / price) throw std::overflow_error("invalid notional");
    return price * quantity / scale_;
}
Amount MatchingEngine::fee(Amount value) const { return (value * fee_ppm_ + ppm_scale - 1) / ppm_scale; }
std::pair<Order, std::vector<Trade>> MatchingEngine::place(const std::string& user,
    const std::string& base, const std::string& quote, Side side, Amount price, Amount quantity) {
    if (user.empty() || base.empty() || quote.empty() || base == quote || price <= 0 || quantity <= 0) throw std::invalid_argument("invalid order");
    std::lock_guard<std::mutex> lock(mutex_);
    Order incoming{next_id_++, next_sequence_++, user, base, quote, side, price, quantity, quantity};
    const Amount max_value = quote_value(price, quantity);
    if (side == Side::Buy) ledger_.reserve(user, quote, add(max_value, fee(max_value))); else ledger_.reserve(user, base, quantity);
    std::sort(orders_.begin(), orders_.end(), [](const Order& a, const Order& b) {
        if (a.side != b.side) return a.side < b.side;
        if (a.price != b.price) return a.side == Side::Buy ? a.price > b.price : a.price < b.price;
        return a.sequence < b.sequence;
    });
    std::vector<Trade> trades;
    for (auto& maker : orders_) {
        if (!incoming.remaining) break;
        if (!maker.remaining || maker.base != base || maker.quote != quote || maker.side == side) continue;
        if ((side == Side::Buy && maker.price > price) || (side == Side::Sell && maker.price < price)) continue;
        const Amount done = std::min(incoming.remaining, maker.remaining), value = quote_value(maker.price, done), charge = fee(value);
        const std::string buyer = side == Side::Buy ? user : maker.user, seller = side == Side::Sell ? user : maker.user;
        ledger_.settle_reserved("base-" + std::to_string(incoming.id) + "-" + std::to_string(maker.id), seller, base, done, {{buyer, base, done}});
        if (side == Side::Buy) {
            const Amount reserved_slice = add(quote_value(incoming.price, done), fee(quote_value(incoming.price, done)));
            const Amount debit = add(value, charge);
            if (reserved_slice > debit) ledger_.release(buyer, quote, reserved_slice - debit);
            ledger_.settle_reserved("quote-" + std::to_string(incoming.id) + "-" + std::to_string(maker.id), buyer, quote, debit, {{seller, quote, value}, {"platform_revenue", quote, charge}});
        }
        else {
            if (charge > 0) ledger_.release(buyer, quote, charge);
            ledger_.settle_reserved("quote-" + std::to_string(incoming.id) + "-" + std::to_string(maker.id), buyer, quote, value, {{seller, quote, value}});
            ledger_.post("fee-" + std::to_string(incoming.id) + "-" + std::to_string(maker.id), {{seller, quote, -charge}, {"platform_revenue", quote, charge}});
        }
        incoming.remaining -= done; maker.remaining -= done; trades.push_back({maker.id, incoming.id, maker.price, done, charge});
    }
    orders_.erase(std::remove_if(orders_.begin(), orders_.end(), [](const Order& o){ return o.remaining == 0; }), orders_.end());
    if (incoming.remaining) orders_.push_back(incoming);
    return {incoming, trades};
}
bool MatchingEngine::cancel(const std::string& user, std::uint64_t id) {
    std::lock_guard<std::mutex> lock(mutex_);
    const auto it = std::find_if(orders_.begin(), orders_.end(), [&](const Order& o){ return o.id == id && o.user == user; });
    if (it == orders_.end()) return false;
    if (it->side == Side::Buy) { const auto value = quote_value(it->price, it->remaining); ledger_.release(user, it->quote, add(value, fee(value))); }
    else ledger_.release(user, it->base, it->remaining);
    orders_.erase(it); return true;
}
std::vector<Order> MatchingEngine::open_orders() const { std::lock_guard<std::mutex> lock(mutex_); return orders_; }
}
