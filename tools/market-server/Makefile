CXX ?= g++
CXXFLAGS ?= -std=c++17 -O2 -Wall -Wextra -Werror -pedantic -pthread

.PHONY: all test clean

all: null-market-server

null-market-server: src/main.cpp src/market_core.cpp src/market_core.hpp src/custody_registry.cpp src/custody_registry.hpp
	$(CXX) $(CXXFLAGS) src/main.cpp src/market_core.cpp src/custody_registry.cpp -o $@

market-core-test: tests/market_core_test.cpp src/market_core.cpp src/market_core.hpp
	$(CXX) $(CXXFLAGS) -Isrc tests/market_core_test.cpp src/market_core.cpp -o $@

custody-registry-test: tests/custody_registry_test.cpp src/custody_registry.cpp src/custody_registry.hpp
	$(CXX) $(CXXFLAGS) -Isrc tests/custody_registry_test.cpp src/custody_registry.cpp -o $@

test: null-market-server market-core-test custody-registry-test
	./market-core-test
	./custody-registry-test
	./tests/smoke_test.sh

clean:
	rm -f null-market-server market-core-test custody-registry-test
