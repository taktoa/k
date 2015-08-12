(use-modules (sxml simple)
             (ice-9 pretty-print)
             (ice-9 format)
             (ice-9 match)
             (ice-9 local-eval)
             (ice-9 receive)
             (ice-9 regex)
             (ice-9 threads)
             (srfi srfi-1))
(use-modules (statprof))

(define debug-enabled      #f)
(define unit-tests-enabled #f)
(define rethrow            #f)

(define-syntax-rule (defsyntax (name syntax-var) body)
  (define-syntax name (λ (syntax-var) body)))
(define-syntax-rule (try (body ...) key-sym (args ...) (handler ...))
  (catch key-sym (λ () body ...) (λ* (args ...) handler ...)))
(define-syntax-rule (try-all (body ...) (key args) (handler ...))
  (try (body ...)
       #t
       (key #:rest args)
       (handler ... (when rethrow (throw key args)))))
(define-syntax-rule (λ* args ...)
  (lambda* args ...))
(define-syntax-rule (thunk args ...)
  (lambda* () args ...))

(define* (head        xs)         (cons (car xs) '()))
(define* (tail        xs)         (cdr xs))
(define* (not-null?   xs)         (not (null? xs)))
(define* (many?       xs)         (> 1 (length xs)))
(define* (one?        xs)         (= 1 (length xs)))
(define* (filter-null xs)         (filter not-null? xs))
(define* (streq?      a b)        (string=? a b))
(define* (const-null  #:rest xs) '())

(define* (printf fmt #:rest args) (apply format `(#t ,fmt ,@args)))
(define* (fmtstr fmt #:rest args)
  (try-all
   ((apply format `(#f ,fmt ,@args)))
   (k a) ((throw 'format-error `(#f "Format error: ~a | ~a" (,fmt ,args) #f)))))

(define* (printfln fmt #:rest args)
  (apply printf `(,(string-append ";; " fmt "\n") ,@args)))

(define* (errorfmt fmt #:rest args)
  (error (apply fmtstr `(,(fmtstr ";; Error: ~s" fmt) ,@args))))

(define* (tryfmt args)
  (match args
    [(_ fmt lst _) (apply fmtstr (cons fmt lst))]
    [_             (fmtstr "~s" args)]))

(define* (run-handler fn-name key args syntax)
  (unless (eqv? key 'quit)
    (printfln "Error in ~a: ~a | ~a" fn-name key (tryfmt args))
    (printfln "Error in ~a:  stx = ~s" fn-name syntax)
    (unless debug-enabled (exit -1))))

(define* (profile prof-thunk
                  #:key
                  (sample-ms    10)
                  (display?     #f)
                  (count-calls? #f)
                  (full-stacks? #f))
  (statprof-stop)
  (receive (sr-s sr-us)
      (floor/ (* 1000 sample-ms) 1000000)
    (statprof-reset sr-s sr-us
                    count-calls?
                    full-stacks?))
  (statprof-start)
  (letrec
      ([statprof-string (thunk (with-output-to-string statprof-display))]
       [sed             regexp-substitute/global]
       [comment-string  (λ* (str) (sed #f "\n" str 'pre "\n;; " 'post))]
       [res             (prof-thunk)])
    (statprof-stop)
    (when display? (display (comment-string (statprof-string))))
    res))

(define* (list-intersperse src-l elem)
  (if (null? src-l) src-l
      (let loop ([l (cdr src-l)]
                 [dest (cons (car src-l) '())])
        (if (null? l) (reverse dest)
            (loop (cdr l) (cons (car l) (cons elem dest)))))))

(define* (cleanup func #:rest args)
  (letrec
      ([fargs   (filter-null (map cleanup-syntax args))]
       [fst     (thunk (car fargs))]
       [err     (λ* (fn) (errorfmt "~s: (~s . ~s)"
                                   fn func fargs))]

       [to-str  (thunk (apply string-append fargs))]
       [to-num  (thunk (cond [(string? (fst)) (string->number (fst))]
                             [(number? (fst)) (fst)                 ]
                             [#t              (err 'to-num)         ]))]
       [to-sym  (thunk (cond [(string? (fst)) (string->symbol (fst))]
                             [(symbol? (fst)) (fst)                 ]
                             [#t              (err 'to-sym)         ]))]
       [to-func (λ* (fn) (thunk `(,fn ,@fargs)))]
       [to-list (to-func 'many)]
       [ident   (to-func func)]
       [to-null (thunk '())])
    (unless (symbol? func) (err 'cleanup))
    (match func
      ['space                 " "      ]
      ['newline               (to-null)]
      ['comment               (to-null)]
      ['introduce             (to-null)]
      ['conditional-if        (to-null)]
      ['conditional-then      (to-null)]
      ['conditional-else      (to-null)]
      ['keyword               (to-str) ]
      ['value                 (to-str) ]
      ['string                (to-str) ]
      ['integer               (to-num) ]
      ['float                 (to-num) ]
      ['name                  (to-str) ]
      ['ref                   (to-str) ]
      ['match-equations       (to-list)]
      ['let-definitions       (to-list)]
      ['let-scope             (to-list)]
      ['letrec-definitions    (to-list)]
      ['letrec-scope          (to-list)]
      ['lam-var               (to-str) ]
      ['lam-vars              (to-list)]
      ['lam-body              (to-list)]
      ['function              (to-str) ]
      ['type-definition-vars  (to-list)]
      ['type-definition-cons  (to-list)]
      ['constructor-arguments (to-list)]
      [_                      (ident)  ])))

(define* (cleanup-syntax stx)
  (if (list? stx) (apply cleanup stx) stx))

(define* (run-cleanup stx)
  (try-all
   ((cleanup-syntax stx))
   (k a) ((run-handler 'run-cleanup k a stx))))

(define* (render func #:rest args)
  (letrec*
      ([err        (λ* [err-sym] (throw err-sym func args))]
       [joining    (λ* [list between]
                       (apply string-append
                              (list-intersperse list between)))]
       [!=         (λ* [#:rest xs] (not (apply = xs)))]
       [largs      (length args)]
       [rdl        (λ* [list] (map render-syntax list))]
       [rd         (thunk (rdl args))]
       [to-list    (thunk (cdr (to-func)))]
       [to-eqn     (thunk (if (= (length args) 2)
                              `[,(car args) => ,@(rdl (cdr args))]
                              (err 'render-error)))]
       [rendf      (λ* [fmt #:rest rs] (apply fmtstr `(,fmt ,@rs)))]
       [rend       (λ* [fmt] (rendf fmt (rd)))]
       [rdef       (λ* [r is-rec]
                       (apply fmtstr
                              `(,(if (= largs 1) "~a ~a;;" "~a ~a in ~a;;")
                                ,(if is-rec "let rec" "let")
                                ,@r)))]
       [rlet       (λ* [r is-rec]
                       (apply fmtstr
                              `("(~a ~a in ~a)"
                                ,(if is-rec "let rec" "let")
                                ,@r)))]

       [to-func    (λ* [#:optional fn]
                       (cons (if (not fn) func fn) (rd)))]
       [otype-var  (λ* [var-name] (printfln "test1: ~s" var-name) (fmtstr "'~a" var-name))]
       [otype-vars (λ* [a] (joining (map otype-var (cdr a)) " "))]
       [otype-name (λ* [a] a)]
       [otype-cons (λ* [a] (joining a " | "))]
       [otype      (λ* [r] (rendf "type ~a ~a = ~a;;\n"
                                  (otype-vars (list-ref args 0))
                                  (otype-name (list-ref args 1))
                                  (otype-cons (rdl
                                               (cdr
                                                (list-ref args 2))))))]
       [ocon-many  (λ* [r] (fmtstr "~a of (~a)"
                                   (car r)
                                   (joining (cdr r) " * ")))]
       [ocon       (λ* [r] (rendf "~a" (if (= 1 largs)
                                           (car r)
                                           (ocon-many r))))]
       [omatch     (thunk  (rendf "(match ~a with ~a)"
                                  (render-syntax (car args))
                                  (joining (rdl
                                            (cdr
                                             (list-ref args 1))) " | ")))]
       [obody      (λ* [r] (joining r "\n"))]
       [omany      (λ* [r] (joining r " "))])
    (try
     ((if (not (symbol? func))
          (rdl (cons func args))
          (match func
            ['body      (obody  (rd))]
            ['many      (omany  (rd))]
            ['type      (otype  (rd))]
            ['con       (ocon   (rd))]
            ['match     (omatch (rd))]
            ['let-in    (rlet   (rd) #f)]
            ['letr-in   (rlet   (rd) #t)]
            ['def       (rdef   (rd) #f)]
            ['defr      (rdef   (rd) #t)]
            ['match-eqn (rend   "~a -> ~a;")]
            ['let-eqn   (rend   "~a = ~a;")]
            ['letr-eqn  (rend   "~a = ~a;")]
            ['lam       (rendf  "(fun ~a -> ~a)"
                                (car args)
                                (rdl (cdr args)))]
            ['cond      (rend   "(if ~a then ~a else ~a)")]
            ['app       (rendf  "~a" `(,(rd)))]
            ['pattern   (joining (rd) "")]
            [_          (err 'render-unmatched)]
            )))
     'format
     (k a) ((printfln "Format error: ~a" a)))))

(define* (render-syntax stx)
  (try-all
   ((if (proper-list? stx) (apply render stx) stx))
   (k a) ((run-handler 'run-render k a stx))))

(define* run-render render-syntax)

(define* (normalize func #:rest args)
  (letrec
      ([norml     (thunk (map normalize-syntax args))]
       [arglen    (thunk (length args))]
       [err       (λ* [fn] (errorfmt "~s: (~s . ~s)" fn func args))]
       [to-rend   (thunk (apply string-append (map run-render (norml))))]
       [to-fst    (thunk
                   (if (one? args)
                       (normalize-syntax (car args))
                       (err 'to-fst)))]
       [to-func   (λ* [#:optional fn]
                      (if (not fn)
                          (to-func func)
                          (cons fn (norml))))]
       [to-func-n (λ* [n #:rest fn]
                      (cond
                       [(and (number? n) (= n (arglen)))
                        (apply to-func-n `((,n) ,@fn))]
                       [(and (list? n) (member (arglen) n))
                        (apply to-func fn)]
                       [#t (err `(to-func-n ,n ,fn))]))])
    (unless (symbol? func) (err 'normalize))
    (match func
      ['body                  (to-func                     )]
      ['many                  (to-func                     )]
      ['rend                  (to-rend                     )]
      ['paren                 (to-fst                      )]
      ['name                  (to-fst                      )]
      ['integer               (to-fst                      )]
      ['float                 (to-fst                      )]
      ['string                (to-fst                      )]
      ['conditional           (to-func-n   3          'cond)]
      ['match-expression      (to-func-n   2         'match)]
      ['match-input           (to-fst                      )]
      ['match-equation        (to-func-n   2     'match-eqn)]
      ['match-equation-val    (to-fst                      )]
      ['match-equation-pat    (to-fst                      )]
      ['let-expression        (to-func-n   2        'let-in)]
      ['let-declaration       (to-func-n '(1 2)        'def)]
      ['let-equation          (to-func-n   2       'let-eqn)]
      ['let-equation-name     (to-fst                      )]
      ['let-equation-val      (to-fst                      )]
      ['letrec-expression     (to-func-n           'letr-in)]
      ['letrec-declaration    (to-func-n '(1 2)       'defr)]
      ['letrec-equation       (to-func-n   2      'letr-eqn)]
      ['letrec-equation-name  (to-fst                      )]
      ['letrec-equation-val   (to-fst                      )]
      ['lam                   (to-func-n   2               )]
      ['application           (to-func                 'app)]
      ['argument              (to-fst                      )]
      ['type-definition       (to-func-n   3          'type)]
      ['type-definition-name  (to-fst                      )]
      ['type-definition-var   (to-fst                      )]
      ['constructor           (to-func-n '(1 2)        'con)]
      ['constructor-name      (to-fst                      )]
      ['constructor-argument  (to-fst                      )]
      [_                      (to-func                     )])))

(define* (normalize-syntax stx) (if (list? stx) (apply normalize stx) stx))

(define* (run-normalize stx)
  (try-all
   ((cons (car stx) (map normalize-syntax (cdr stx))))
   (k a) ((run-handler 'run-normalize k a stx))))

(define* (renormalize func #:rest args)
  (letrec
      ([norml     (λ* (list) (map renormalize-syntax list))]
       [norm      (thunk (norml args))]
       [to-list   (thunk (cdr (to-func)))]
       [to-eqn    (thunk (if (= (length args) 2)
                             `[,(car args) => ,@(norml (cdr args))]
                             (throw 'renormalize-error func args)))]
       [to-func   (λ* [#:optional fn]
                      (cons (if (not fn) func fn) (norm)))])
    (if (not (symbol? func)) (norml (cons func args))
        (match func
          ['many        (to-list        )]
          ['cond        (to-func     'if)]
          ['match-eqn   (to-eqn         )]
          ['let-eqn     (to-eqn         )]
          ['letr-eqn    (to-eqn         )]
          ['let-in      (to-func   'letn)]
          ['letr-in     (to-func   'letr)]
          ['def         (to-func   'letn)]
          ['defr        (to-func   'letr)]
          [_            (to-func        )]))))

(define* (renormalize-syntax stx)
  (if (list? stx) (apply renormalize stx) stx))

(define* (run-renormalize stx)
  (try-all
   ((renormalize-syntax stx))
   (k a) ((run-handler 'run-renormalize k a stx))))

(define* (read-xml path)
  (let* ([port (open-file path "r")]
         [xml (cdr (xml->sxml port))])
    (close-port port)
    xml))

(define* (process-data input-data)
  (let* ([cleaned (run-cleanup     input-data)]
         [normal  (run-normalize      cleaned)]
         [final   (run-renormalize     normal)]
         [render  (run-render          normal)])
    `(,final . ,render)))

(define* (main)
  (try-all
   ((catch 'exit
      (thunk
       (unless (= (length (command-line)) 2)
         (printfln "Usage: normalize.scm <path-to-xml>")
         (throw 'exit 1))

       (let ([input-path (list-ref (command-line) 1)])
         (unless (access? input-path R_OK)
           (printfln "File not found. Quitting.")
           (throw 'exit 2))
         (let ([proc (process-data (car (read-xml input-path)))])
           (pretty-print (car proc))
           (printfln "")
           (printfln "")
           (printfln "")
           (printfln "\n~a" (cdr proc)))
         (throw 'exit 0)))

      (λ* (key code)
          (printfln "Exit code: ~s" code)
          (exit code))))
   (k a) ((printfln "Error in main: ~a | ~a" k (tryfmt a)))))

;; Unit test input data and the relevant expected output from `process-data'

(define* (gentest function input expected)
  (let ([output (function input)])
    (if (equal? output expected)
        #t
        (begin
          (printfln "Input:")
          (pretty-print input)
          (printfln "Expected:")
          (pretty-print expected)
          (printfln "Output:")
          (pretty-print output)
          #f))))

(define unit-test-1-input
  '(body
    (application (function "hello")
                 (argument "a")
                 (comment "comment")
                 (introduce "x")
                 (argument "b")
                 (argument "c"))
    (application (function "goodbye")
                 (argument "x")
                 (argument "y"))))

(define unit-test-1-expected
  '(body (app "hello" "a" "b" "c") (app "goodbye" "x" "y")))

(define* (unit-test-1)
  (gentest process-data
           unit-test-1-input
           unit-test-1-expected))

(define unit-test-2-input
  '(body
    (type-definition
     (type-definition-name (name "sort"))
     (type-definition-vars)
     (type-definition-cons
      (constructor
       (constructor-name "SortId"))
      (constructor
       (constructor-name "SortStmt"))))))

(define unit-test-2-expected
  '(body (type "sort" () ((con "SortId") (con "SortStmt")))))

(define* (unit-test-2)
  (gentest process-data
           unit-test-2-input
           unit-test-2-expected))

(define unit-test-3-input
  '(body
    (let-declaration
     (let-definitions
      (let-equation
       (let-equation-name "testValue")
       (let-equation-val
        (application
         (lam
          (lam-vars
           (lam-var "x")
           (lam-var "y"))
          (lam-body
           (application
            (function "add")
            (argument "x")
            (argument "y"))))
         (argument (integer "5"))
         (argument (integer "7")))))))))

(define unit-test-3-expected
  '(body (letn (("testValue" => (app (lam ("x" "y")
                                          ((app "add" "x" "y")))
                                     5 7))))))

(define* (unit-test-3)
  (gentest process-data
           unit-test-3-input
           unit-test-3-expected))

(define unit-test-4-input
  '(body
    (let-declaration
     (let-definitions
      (let-equation
       (let-equation-name "testValue")
       (let-equation-val
        (match-expression
         (match-input "True")
         (match-equations
          (match-equation
           (match-equation-pat
            (rend "True"
                  (space)
                  "when"
                  (space)
                  (rend
                   (application
                    (function "test")
                    (argument "x")))))
           (match-equation-val (integer "0")))
          (match-equation
           (match-equation-pat "True")
           (match-equation-val (integer "1")))))))))))

(define unit-test-4-expected
  '(body (letn (("testValue" =>
                 (match "True"
                   (("True when (test x)" => 0)
                    ("True" => 1))))))))

(define* (unit-test-4)
  (gentest process-data
           unit-test-4-input
           unit-test-4-expected))

(define unit-test-5-input
  '(body
    (letrec-declaration
     (letrec-definitions
      (letrec-equation
       (letrec-equation-name "testValue")
       (letrec-equation-val "5"))))))

(define unit-test-5-expected
  '(body (letr (("testValue" => "5")))))

(define* (unit-test-5)
  (gentest process-data
           unit-test-5-input
           unit-test-5-expected))

(define* (unit-tests)
  (try-all
   ((let ([run-unit-test (λ* [test-name test-runner]
                             (printfln "Running test: ~a" test-name)
                             (unless (test-runner) (throw 'unit-test
                                                          test-name)))])
      (run-unit-test "unit test 1" unit-test-1)
      (run-unit-test "unit test 2" unit-test-2)
      (run-unit-test "unit test 3" unit-test-3)
      (run-unit-test "unit test 4" unit-test-4)
      (run-unit-test "unit test 5" unit-test-5)
      #t))
   (k a) ((match k
            ['unit-test (printfln "Error in unit test: ~a" (car a))]
            [_          (apply throw (cons k a))                  ]))))



(when unit-tests-enabled (unit-tests))

(unless debug-enabled (main))
