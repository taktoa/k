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

K = $(MDIR)/k-distribution/target/release/k/lib/k

KOMPILE-FLAGS = --backend func --debug -v
KOMPILE = $(K) -kompile $(KOMPILE-FLAGS)

all:
	echo "Doing nothing."

build:
	$(MAVEN) $(PACKAGE) $(PACKAGE-FLAGS)

test:
	$(MAVEN) $(TEST) $(TEST-FLAGS)

calc:
	cd k-distribution/tests/regression/func-backend/calc; \
	$(KOMPILE) calc.k; \
	rm -rf calc-kompiled .kompile-*

doc:
	$(MAVEN) $(DOC) $(DOC-FLAGS)
