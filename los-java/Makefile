OUT ?= out/java
MVN ?= ./mvnw

.PHONY: build run parity
build:
	$(MVN) -B -DskipTests package

run: build
	./run-chain.sh "$(OUT)"

parity: build
	python3 -B -m unittest test_parity
	@rc=0; ./run-chain.sh "$(OUT)" || rc=$$?; \
	  python3 parity.py "$(OUT)" --chain-rc $$rc
