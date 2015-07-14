ENV = /usr/bin/env
GNUTIME = ${ENV} time
TEE = tee
CAT = cat
MAKE = make
FIND = find
XARGS = xargs
MAVEN = mvn -T 8
KOMPILE = kompile
KRUN = krun

PACKAGE = package
PACKAGE_FLAGS = -DskipKTest -Dtest.skip=true -Dtests.skip=true -DskipTests
TEST = verify
TEST_FLAGS = -DskipKTest -Dcheckstyle.skip=true
DOC = javadoc:aggregate
DOC_FLAGS = -Dmaven.javadoc.failOnError=false

MDIR := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
PDIR := $(shell dirname ${MDIR})

KROOT = ${MDIR}/k-distribution/target/release/k

all: build test func-test

build: package etags doc

package: func-test-clean
	${MAVEN} ${PACKAGE} ${PACKAGE_FLAGS}

test: func-test-clean
	${MAVEN} ${TEST} ${TEST_FLAGS}

doc: func-test-clean
	${MAVEN} ${DOC} ${DOC_FLAGS}

etags: func-test-clean
	${FIND} . -name '*.java' | ${XARGS} etags






FUNC_TEST_FILE_NAME = kore_imp
FUNC_TEST_MODULE = IMP
FUNC_TEST_FILE_DIR = ${MDIR}/k-distribution/tests/regression/func-backend
FUNC_TEST_FILE = ${FUNC_TEST_FILE_DIR}/${FUNC_TEST_FILE_NAME}.k
FUNC_TEST_INPUT = ${FUNC_TEST_FILE_DIR}/${FUNC_TEST_FILE_NAME}_1.imp

FUNC_TEST_TMPDIR_TEMPLATE = func-test.
FUNC_TEST_TMPDIR := $(shell mktemp --tmpdir -d "${FUNC_TEST_TMPDIR_TEMPLATE}XXXXXX")

FUNC_TEST_KOMPILE_PARAMS = ${FUNC_TEST_FILE_NAME}.k --main-module ${FUNC_TEST_MODULE}
FUNC_TEST_KOMPILE_OPTS = --kore --backend func --debug
FUNC_TEST_KRUN_PARAMS = ${FUNC_TEST_INPUT}
FUNC_TEST_KRUN_OPTS = --kore --debug

FUNC_TEST_LOG_DIR = ${PDIR}/func-test-logs
FUNC_TEST_KOMPILE_TIME_FILE = ${FUNC_TEST_LOG_DIR}/kompile-time.log
FUNC_TEST_KRUN_TIME_FILE = ${FUNC_TEST_LOG_DIR}/krun-time.log
FUNC_TEST_KOMPILE_LOG_FILE = ${FUNC_TEST_LOG_DIR}/kompile.log
FUNC_TEST_KRUN_LOG_FILE = ${FUNC_TEST_LOG_DIR}/krun.log

FUNC_TEST_KOMPILE_GNUTIME = ${GNUTIME} --output ${FUNC_TEST_KOMPILE_TIME_FILE} --verbose
FUNC_TEST_KRUN_GNUTIME = ${GNUTIME} --output ${FUNC_TEST_KRUN_TIME_FILE} --verbose

FUNC_TEST_KOMPILE_CMD = ${FUNC_TEST_KOMPILE_GNUTIME} ${KOMPILE} ${FUNC_TEST_KOMPILE_PARAMS} ${FUNC_TEST_KOMPILE_OPTS}
FUNC_TEST_KRUN_CMD = ${FUNC_TEST_KRUN_GNUTIME} ${KRUN} ${FUNC_TEST_KRUN_PARAMS} ${FUNC_TEST_KRUN_OPTS}

func-test-clean:
	rm -rvf /tmp/${FUNC_TEST_TMPDIR_TEMPLATE}*

func-test-prepare:
	cp ${FUNC_TEST_FILE} ${FUNC_TEST_TMPDIR}
	mkdir -p ${FUNC_TEST_LOG_DIR}

func-test-kompile:
	cd ${FUNC_TEST_TMPDIR} && ${FUNC_TEST_KOMPILE_CMD} |& ${TEE} ${FUNC_TEST_KOMPILE_LOG_FILE} | ${CAT}

func-test-krun:
	cd ${FUNC_TEST_TMPDIR} && ${FUNC_TEST_KRUN_CMD} |& ${TEE} ${FUNC_TEST_KRUN_LOG_FILE} | ${CAT}

func-test: func-test-prepare func-test-kompile func-test-krun
	cp ${FUNC_TEST_TMPDIR}/${FUNC_TEST_FILE_NAME}-kompiled/def.ml ${FUNC_TEST_LOG_DIR}/def.ml
	cp ${FUNC_TEST_TMPDIR}/.krun*/pgm.ml ${FUNC_TEST_LOG_DIR}/pgm.ml
	cp ${FUNC_TEST_TMPDIR}/.krun*/compile.err ${FUNC_TEST_LOG_DIR}/compile.err
	cp ${FUNC_TEST_TMPDIR}/.krun*/compile.out ${FUNC_TEST_LOG_DIR}/compile.out
	make func-test-clean



C_SEMANTICS_DIR = ${PDIR}/c-semantics
C_SEMANTICS_TIME_FILE = ${PDIR}/c-semantics-logs/time.log
C_SEMANTICS_LOG_FILE = ${PDIR}/c-semantics-logs/run.log

C_SEMANTICS_GNUTIME = ${GNUTIME} --output ${C_SEMANTICS_TIME_FILE} --verbose

C_SEMANTICS_TEST = ${C_SEMANTICS_GNUTIME} ${MAKE} |& ${TEE} ${C_SEMANTICS_LOG_FILE} | ${CAT}

c-semantics-dir: ${C_SEMANTICS_DIR}

c-semantics-clean: c-semantics-dir
	cd ${C_SEMANTICS_DIR} && ${MAKE} clean

c-semantics-test: c-semantics-dir
	cd ${C_SEMANTICS_DIR} && ${C_SEMANTICS_TEST}
