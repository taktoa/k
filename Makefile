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

TEST_FILE_NAME = kore_imp
TEST_FILE_DIR = $(MDIR)/k-distribution/src/test/resources/convertor-tests
TEST_FILE = $(TEST_FILE_DIR)/$(TEST_FILE_NAME).k

TEST_MODULE = IMP


KROOT = $(MDIR)/k-distribution/target/release/k
KOMPILE = $(KROOT)/lib/k -kompile 

func-test:
	export TMPDIR=$$(mktemp -d); \
	export OLDDIR=$$(pwd); \
	cp $(TEST_FILE) $$TMPDIR/; \
	cd $$TMPDIR; \
	$(KOMPILE) $(TEST_FILE_NAME).k \
	           --main-module $(TEST_MODULE) \
	           --kore --backend ocaml; \
	cat $(TEST_FILE_NAME)-kompiled/def.ml; \
	cd $$OLDDIR; \
	rm -rf $$TMPDIR
