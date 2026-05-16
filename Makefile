GRADLE ?= ./gradlew
VERSION    := $(shell cat VERSION)
APK_DEBUG  := app/build/outputs/apk/debug/app-$(VERSION)-debug.apk

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


lint:
	$(GRADLE) lint

test:
	$(GRADLE) test

bundle:
	$(GRADLE) bundleRelease

# One-time setup if ./gradlew is missing. Requires a system-wide `gradle`.
wrapper:
	gradle wrapper --gradle-version 8.10.2
