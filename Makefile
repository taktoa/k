FLAGS = -T 8

MAVEN-NAME = mvn

MAVEN = $(MAVEN-NAME) $(FLAGS)

JAVA = java
JAVA-OPTIONS = -Djava.awt.headless=true -Xms64m -Xmx1024m -Xss32m -XX:+TieredCompilation -ea
JAVA-CP = -cp "k-distribution/target/release/k/lib/java/*"
JAVA-RUN = $(JAVA) $(JAVA-OPTIONS) $(JAVA-CP)

PACKAGE = package
PACKAGE-FLAGS = -DskipKTest -Dtest.skip=true -Dtests.skip=true -DskipTests
TEST = verify
TEST-FLAGS = -DskipKTest -Dcheckstyle.skip=true
DOC = javadoc:aggregate
DOC-FLAGS = -Dmaven.javadoc.failOnError=false

MDIR := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))

K = $(MDIR)/k-distribution/target/release/k/lib/k

KOMPILE-FLAGS = --debug -v --kore
KOMPILE = $(K) -kompile $(KOMPILE-FLAGS)

FUNC-TEST-DIR = $(MDIR)/k-distribution/tests/regression/func-backend

all:
	echo "Doing nothing."

build:
	$(MAVEN) $(PACKAGE) $(PACKAGE-FLAGS)

test:
	$(MAVEN) $(TEST) $(TEST-FLAGS)

# run-ocaml:
#  	$(JAVA-RUN) org.kframework.backend.ocaml.compile.DefinitionToOcaml ${ARGS}
#  
#  
# ktest:
#  	cd $(FUNC-TEST-DIR)/${TEST_NAME}; $(KOMPILE) ${TEST_NAME}.k ${KARGS}; rm -rf .kompile-*
#  	make run-ocaml ARGS="$(FUNC-TEST-DIR)/${TEST_NAME}/${TEST_NAME}-kompiled/compile.bin"
#  	rm -rf $(FUNC-TEST-DIR)/${TEST_NAME}/${TEST_NAME}-kompiled

doc:
	$(MAVEN) $(DOC) $(DOC-FLAGS)
