GRADLE ?= ./gradlew
VERSION    := $(shell cat VERSION)
APK_DEBUG  := app/build/outputs/apk/debug/ospchat-$(VERSION)-debug.apk

.PHONY: help build debug clean install lint test bundle wrapper

help:
	@echo "OSPChat — common tasks"
	@echo ""
	@echo "  make build     - assemble debug APK"
	@echo "  make install   - install debug APK on the connected device"
	@echo "  make clean     - gradle clean"
	@echo "  make lint      - run Android lint"
	@echo "  make test      - run unit tests"
	@echo "  make bundle    - assemble release AAB"
	@echo "  make wrapper   - generate the Gradle wrapper (one-time setup)"

build: debug

debug:
	$(GRADLE) assembleDebug

clean:
	$(GRADLE) clean

#install:
#	$(GRADLE) installDebug

install: debug
	adb install -r $(APK_DEBUG)


lint: ktlint

gradle-lint:
	$(GRADLE) lint

ktlint:
	@ktlint app/

tools: ktlint-tool

ktlint-tool:
	@curl -sSLO https://github.com/pinterest/ktlint/releases/download/1.8.0/ktlint && chmod a+x ktlint && mv ktlint $(shell go env GOPATH)/bin

test:
	$(GRADLE) test

bundle:
	$(GRADLE) bundleRelease

tag:
	@echo "Tagging the current version..."
	git tag -a "v$(VERSION)" -m "Release version $(VERSION)"; \
	git push origin "v$(VERSION)"
