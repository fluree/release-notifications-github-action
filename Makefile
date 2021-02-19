.PHONY: clean

SOURCES := $(shell find src)

index.js: $(SOURCES) node_modules
	clojure -M:nodejs

node_modules: package.json package-lock.json
	npm install && touch node_modules

clean:
	rm -rf index.js
	rm -rf node_modules
