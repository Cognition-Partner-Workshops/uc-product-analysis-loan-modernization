# Legacy LOS nightly batch on GnuCOBOL (apt-get install gnucobol)
.PHONY: legacy golden-check parity inputs clean

legacy:            ## compile and run the chain into out/legacy
	./run-chain.sh out/legacy

golden-check: legacy  ## the legacy run must reproduce the committed golden files
	python3 tools/parity.py out/legacy

parity:            ## compare a new implementation's output: make parity CANDIDATE=dir
	python3 tools/parity.py $(CANDIDATE) --report $(CANDIDATE)/parity.md

inputs:            ## regenerate the recorded inputs (changes the golden files)
	python3 tools/gen_inputs.py

clean:
	rm -rf out
