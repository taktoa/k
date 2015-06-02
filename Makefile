FLAGS = -T 8

MAVEN-NAME = mvn

MAVEN = $(MAVEN-NAME) $(FLAGS)

PACKAGE = package
PACKAGE-FLAGS = -DskipKTest -Dtest.skip=true -Dtests.skip=true -DskipTests
TEST = verify
TEST-FLAGS = -DskipKTest -Dcheckstyle.skip=true
DOC = javadoc:aggregate
DOC-FLAGS = -Dmaven.javadoc.failOnError=false

MDIR := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))

all:
	echo "Doing nothing."

build:
	$(MAVEN) $(PACKAGE) $(PACKAGE-FLAGS)

test:
	$(MAVEN) $(TEST) $(TEST-FLAGS)

doc:
	$(MAVEN) $(DOC) $(DOC-FLAGS)

KROOT = $(MDIR)/k-distribution/target/release/k

TEST_FILE_NAME = kore_imp
TEST_MODULE = IMP
TEST_FILE_DIR = $(MDIR)/k-distribution/tests/regression/func-backend
TEST_FILE = $(TEST_FILE_DIR)/$(TEST_FILE_NAME).k
TEST_INPUT = $(TEST_FILE_DIR)/$(TEST_FILE_NAME)_1.imp

func-test:
	export TMPDIR=$$(mktemp -d); \
	export OLDDIR=$$(pwd); \
	export PATH=$$PATH:$(KROOT)/bin; \
	cp $(TEST_FILE) $$TMPDIR/; \
	cd $$TMPDIR; \
	kompile $(TEST_FILE_NAME).k \
	        --main-module $(TEST_MODULE) \
	        --kore --backend func; \
	krun $(TEST_INPUT) --kore; \
	cd $$OLDDIR; \
	rm -rf $$TMPDIR
