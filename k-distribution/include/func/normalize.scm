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


(define-syntax-rule (defsyntax (name syntax-var) body)
  (define-syntax name (λ (syntax-var) body)))
(define-syntax-rule (try (body ...) key-sym (args ...) (handler ...))
  (catch key-sym (λ () body ...) (λ* (args ...) handler ...)))
(define-syntax-rule (try-all (body ...) (key args) (handler ...))
  (try (body ...) #t (key #:rest args) (handler ... (throw key args))))
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
(define* (fmtstr fmt #:rest args) (apply format `(#f ,fmt ,@args)))

(define* (printfln fmt #:rest args)
  (apply printf `(,(string-append ";; " fmt "\n") ,@args)))

(define* (errorfmt fmt #:rest args)
  (error (apply fmtstr `(,(fmtstr ";; Error: ~s" fmt) ,@args))))

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
      ((statprof-string (thunk (with-output-to-string statprof-display)))
       (sed             regexp-substitute/global)
       (comment-string  (λ* (str) (sed #f "\n" str 'pre "\n;; " 'post)))
       (res             (prof-thunk)))
    (statprof-stop)
    (when display? (display (comment-string (statprof-string))))
    res))

(define* (list-intersperse src-l elem)
  (if (null? src-l) src-l
      (let loop ((l (cdr src-l)) (dest (cons (car src-l) '())))
        (if (null? l) (reverse dest)
            (loop (cdr l) (cons (car l) (cons elem dest)))))))

(define test-input-1 '(body
                       (application (function "hello")
                                    (argument "a")
                                    (comment "comment")
                                    (introduce "x")
                                    (argument "b")
                                    (argument "c"))
                       (application (function "goodbye")
                                    (argument "x")
                                    (argument "y"))))

(define test-input-2 '(body
                       (type-definition
                        (type-definition-name (name "sort"))
                        (type-definition-vars)
                        (type-definition-cons
                         (constructor
                          (constructor-name "SortId"))
                         (constructor
                          (constructor-name "SortStmt"))))))

(define test-input-3 '(body
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


(define test-input-4 '(body
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
                              (match-equation-val "0"))
                             (match-equation
                              (match-equation-pat "True")
                              (match-equation-val "1"))))))))))

(define test-input-5 '(body
                       (letrec-declaration
                        (letrec-definitions
                         (letrec-equation
                          (letrec-equation-name "testValue")
                          (letrec-equation-val "5"))))))

(define* (cleanup-syntax stx)
  (try-all
   ((letrec
        ([add-c-err  (λ* (str) (errorfmt "add-c: ~s" str))]
         [add-c      (λ* (#:rest args)
                         (when (null? args) (add-c-err "not enough args"))
                         `(cleanup ',(car args)
                                   ,@(map (λ* (x) (if (list? x)
                                                      (add-c-list x)
                                                      x))
                                          (cdr args))))]
         [add-c-list (λ* (xs) (apply add-c xs))]

         [cleanup    (λ* (func #:rest args)
                         (letrec
                             ([fargs   (filter-null args)]
                              [fst     (thunk (car fargs))]
                              [err     (λ* (fn) (errorfmt "~s: (~s . ~s)"
                                                          fn func fargs))]
                              [pnum    string->number]
                              [psym    string->symbol]
                              [to-str  (thunk (apply string-append fargs))]
                              [to-num  (thunk
                                        (cond
                                         ((string? (fst)) (pnum (fst)))
                                         ((number? (fst)) (fst))
                                         (#t              (err 'to-num))))]
                              [to-sym  (thunk
                                        (cond
                                         ((string? (fst)) `',(psym (fst)))
                                         ((symbol? (fst)) (fst))
                                         (#t              (err 'to-sym))))]
                              [to-func (λ* (fn) (thunk `(,fn ,@fargs)))]
                              [to-list (to-func 'many)]
                              [ident   (to-func func)]
                              [to-null (thunk '())])
                           (match func
                             ['space                 " "]
                             ['newline               (to-null)]
                             ['comment               (to-null)]
                             ['introduce             (to-null)]
                             ['keyword               (to-str)]
                             ['value                 (to-str)]
                             ['string                (to-str)]
                             ['integer               (to-num)]
                             ['float                 (to-num)]
                             ['name                  (to-str)]
                             ['ref                   (to-str)]
                             ['match-equations       (to-list)]
                             ['let-definitions       (to-list)]
                             ['let-scope             (to-list)]
                             ['letrec-definitions    (to-list)]
                             ['letrec-scope          (to-list)]
                             ['lam-var               (to-str)]
                             ['lam-vars              (to-list)]
                             ['lam-body              (to-list)]
                             ['function              (to-str)]
                             ['type-definition-vars  (to-list)]
                             ['type-definition-cons  (to-list)]
                             ['constructor-arguments (to-list)]
                             [_                      (ident)])))])
      (local-eval (if (list? stx) (add-c-list stx) stx) (the-environment))))

   (k a)
   ((printfln ";; Error in cleanup-syntax: key = ~s, args = ~s" k a))))

(define* (run-cleanup stx)
  (cons (car stx) (par-map cleanup-syntax (cdr stx))))


(define* (render-syntax stx)
  (try-all
   ((letrec
        ((joining     (λ* (list between)
                          (apply string-append
                                 (list-intersperse list between))))
         (render      (λ* (x) (render-syntax x)))
         (type-vars   (λ* (vs) (joining (map render vs) ", ")))
         (type-cons   (λ* (cs) (joining (map render cs) " | ")))
         (con-args    (λ* (as) (joining (map render as) " * ")))
         (app-args    (λ* (as) (joining (map render as) " ")))

         (type        (λ* (n vs cs)
                          (fmtstr "type ~a ~a = ~a;;\n"
                                  (if (null? vs)
                                      ""
                                      (string-append
                                       "(" (type-vars vs) ")"))
                                  n (type-cons cs))))
         (con         (λ* (cn as)
                          (if (null? as)
                              cn
                              (fmtstr "~s of (~s)" cn (con-args as)))))
         (conditional (λ* (c t f)
                          (fmtstr "(if ~s then ~s else ~s)" c t f)))
         (match       (λ* (v es) (fmtstr "(match ~a with ~a)"
                                         v (joining (map render es)
                                                    " | "))))
         (match-eqn   (λ* (p v)  (fmtstr "~a -> ~a" p v)))
         (let-in      (λ* (es s) (fmtstr "(let ~a in ~a)"
                                         (joining (map render es) "; ") s)))
         (def         (λ* (es #:optional (s '()))
                          (if (null? s)
                              (fmtstr "let ~a;;"
                                      (joining (map render es) "; "))
                              (fmtstr "let ~a in ~a;;"
                                      (joining (map render es) "; ") s))))
         (let-eqn     (λ* (n v) (fmtstr "~a = (~a)" n v)))
         (letr-in   (λ* (es s) (fmtstr "(let rec ~a in ~a)"
                                       (joining (map render es) "; ") s)))
         (defr        (λ* (es #:optional (s '()))
                          (if (null? s)
                              (fmtstr "let rec ~a;;"
                                      (joining (map render es) "; "))
                              (fmtstr "let rec ~a in ~a;;"
                                      (joining (map render es) "; ") s))))
         (letr-eqn    (λ* (n v) (fmtstr "~a = (~a)" n v)))
         (lam         (λ* (vs bd) (fmtstr "(fun ~a -> ~a)"
                                          (joining (map symbol->string vs) " ")
                                          (map render bd))))
         (app         (λ* (f #:rest as)
                          (if (symbol? f)
                              (fmtstr "(~a ~a)"
                                      (symbol->string f)
                                      (app-args as))
                              (fmtstr "(~a ~a)"
                                      (render f)
                                      (app-args as))))))
      (local-eval stx (the-environment))))
   (k a)
   ((printfln "Error in render-syntax: key = ~s, args = ~s" k a))))















(define* (normalize func #:rest args)
  (catch 'return
    (thunk
     (letrec
         ([return    (λ* (retval) (throw 'return retval))]
          [arglen    (thunk (length args))]
          [err       (λ* [fn] (errorfmt "~s: (~s . ~s)" fn func args))]
          [render    (λ* (x) (render-syntax (normalize-syntax x)))]
          [to-rend   (thunk (apply string-append (map render args)))]
          [to-fst    (thunk
                      (if (one? args)
                          (normalize-syntax (car args))
                          (err 'to-fst)))]
          [to-func   (λ* [#:optional fn]
                         (if (not fn)
                             (to-func func)
                             (cons fn (map normalize-syntax args))))]
          [to-func-n (λ* [n #:rest fn]
                         (cond
                          [(and (number? n) (= n (arglen)))
                           (apply to-func-n `((,n) ,@fn))]
                          [(and (list? n) (member (arglen) n))
                           (apply to-func fn)]
                          [#t (err `(to-func-n ,n ,fn))]))])
       (when (string? func)
         (apply string-append `(,func ,@(map normalize-syntax args))))
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
         ['conditional           (to-func-n   3   'conditional)]
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
    (λ (retval) retval)))

(define* (normalize-syntax stx) (if (list? stx) (apply normalize stx) stx))

(define* (run-normalize stx)
  (try-all
   ((cons (car stx) (par-map normalize-syntax (cdr stx))))
   (k a) ((printfln ";; Error in run-normalize: key = ~s, args = ~s" k a))))



(define* (renormalize func #:rest args)
  (letrec
      ([to-list   (thunk (cdr (to-func)))]
       [to-func   (λ* [#:optional fn]
                      (if (not fn)
                          (to-func func)
                          (cons fn (map renormalize-syntax args))))])
    (if (not (symbol? func)) (map renormalize-syntax (cons func args))
        (match func
          ['many        (to-list        )]
          ['conditional (to-func     'if)]
          ['match-eqn   (to-func    'eqn)]
          ['let-eqn     (to-func    'eqn)]
          ['letr-eqn    (to-func    'eqn)]
          ['let-in      (to-func    'let)]
          ['letr-in     (to-func 'letrec)]
          ['def         (to-func    'let)]
          ['defr        (to-func 'letrec)]
          [_            (to-func        )]))))

(define* (renormalize-syntax stx)
  (if (list? stx) (apply renormalize stx) stx))

(define* (run-renormalize stx)
  (cons (car stx) (par-map renormalize-syntax (cdr stx))))













;; (define* (normalize-syntax stx)
;;   (let*
;;       ((inside-error          (λ* (tag xs)
;;                                   (errorfmt "inside: ~s | ~s" tag xs)))
;;        (inside                (λ* (tag) (λ* (x) x)))
;;        (to-func               (λ* (func)
;;                                   (λ* (#:rest args)
;;                                       `(,func ,@(map (λ* (x) `',x)
;;                                                      (filter-null args))))))
;;        (to-func-n-error       (thunk
;;                                (errorfmt
;;                                 "to-func-n: wrong number of arguments")))
;;        (to-func-n             (λ* (func n)
;;                                   (λ* (#:rest args)
;;                                       (let ((result (thunk
;;                                                      (apply (to-func func)
;;                                                             args)))
;;                                             (error  to-func-n-error))
;;                                         (cond
;;                                          ((= (length args) n) (result))
;;                                          (#t                  (error)))))))


;;        (body                  (λ* (#:rest xs) `'(,@xs)))
;;        (many                  (to-func   'many))
;;        (paren                 (inside    'paren))
;;        (name                  (inside    'name))
;;        (integer               (inside    'integer))
;;        (float                 (inside    'float))
;;        (string                (inside    'string))
;;        (conditional           (to-func-n 'conditional 3))
;;        (match-expression      (to-func-n 'match 2))
;;        (match-input           (inside    'match-input))
;;        (match-equation        (to-func-n 'match-eqn 2))
;;        (match-equation-val    (inside    'match-equation-val))
;;        (match-equation-pat    (inside    'match-equation-pat))
;;        (let-expression        (to-func-n 'let-in 2))
;;        (let-declaration       (λ* (d #:optional s) (if (null? s)
;;                                                        `(def ',d ',s)
;;                                                        `(def ',d))))
;;        (let-equation          (to-func-n 'let-eqn 2))
;;        (let-equation-name     (inside    'let-equation-name))
;;        (let-equation-val      (inside    'let-equation-val))
;;        (letrec-expression     (to-func-n 'letrec-in 2))
;;        (letrec-declaration    (λ* (d #:optional s) (if (null? s)
;;                                                        `(defrec ',d ',s)
;;                                                        `(defrec ',d))))
;;        (letrec-equation       (to-func-n 'letrec-eqn 2))
;;        (letrec-equation-name  (inside    'letrec-equation-name))
;;        (letrec-equation-val   (inside    'letrec-equation-val))
;;        (lam                   (to-func-n 'lam 2))
;;        (application           (to-func   'app))
;;        (argument              (inside    'argument))
;;        (type-definition       (to-func-n 'type 3))
;;        (type-definition-name  (inside    'type-definition-name))
;;        (type-definition-var   (inside    'type-definition-vars))
;;        (constructor           (to-func-n 'con 2))
;;        (constructor-name      (inside    'constructor-name))
;;        (constructor-argument  (inside    'constructor-argument)))
;;     (local-eval stx (the-environment))))

;; (define* (prerender-syntax stx)
;;   (letrec
;;       ((add-p-err  (λ* (str) (errorfmt "add-p: ~s" str)))
;;        (add-p      (λ* (#:rest args)
;;                        (when (null? args) (add-p-err "not enough args"))
;;                        `(process ',(car args)
;;                                  ,@(map (λ* (x)
;;                                             (if (list? x) (add-p-list x) x))
;;                                         (cdr args)))))
;;        (add-p-list (λ* (xs) (apply add-p xs)))

;;        (render     (λ (x) (render-syntax (normalize-syntax x))))
;;        (process    (λ* (func #:rest args)
;;                        (let* ((to-rend (thunk (apply string-append
;;                                                      (par-map render args))))
;;                               (ident   (thunk `(,func ,@args))))
;;                          (match func
;;                            ('rend (to-rend))
;;                            (_     (ident)))))))
;;     (local-eval (add-p-list stx) (the-environment))))

(define* (read-xml path)
  (let* ((port (open-file path "r"))
         (xml (cdr (xml->sxml port))))
    (close-port port)
    xml))

(define* (process-data input-data)
  (let* ([cleaned (run-cleanup input-data)]
         [normal  (run-normalize cleaned)])
;;         [final   (run-renormalize normal)])
    (pretty-print normal)))

;;  (let* ((clean-data (begin
;;                       (printfln ";; BEGIN PROFILING CLEAN-SYNTAX")
;;                       (profile (thunk (cleanup-syntax input-data))
;;                                #:sample-ms    100
;;                                #:display?     #t
;;                                #:count-calls? #t)))
;;
;;         (norm-data  (begin
;;                       (printfln ";; BEGIN PROFILING NORMALIZE-SYNTAX")
;;                       (profile (thunk (normalize-syntax clean-data))
;;                                #:sample-ms    100
;;                                #:display?     #t
;;                                #:count-calls? #t))))
;;    (display "\n\n\n\n\n")


(define* (main)
  (try-all
   ((catch 'exit
      (thunk
       (unless (= (length (command-line)) 2)
         (printfln "Usage: normalize.scm <path-to-xml>")
         (throw 'exit 1))

       (let ((input-path (list-ref (command-line) 1)))
         (unless (access? input-path R_OK)
           (printfln "File not found. Quitting.")
           (throw 'exit 2))
         (process-data (car (read-xml input-path)))
         (throw 'exit 0)))

      (λ* (key code)
          (printfln "Exit code: ~s" code)
          (exit code))))
   (k a)
   ((printfln ";; Error in main: key = ~s, args = ~s" k a))))

;; (define* (test-func x)
;;   (pretty-print (cleanup-syntax x))
;;   (pretty-print (prerender-syntax (cleanup-syntax x))))

(main)
