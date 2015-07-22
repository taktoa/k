(use-modules (sxml simple)
             (ice-9 pretty-print)
             (ice-9 format)
             (ice-9 match)
             (ice-9 local-eval)
             (srfi srfi-1))
(use-modules (statprof))

(define-syntax-rule (λ* args ...) (lambda* args ...))

(define head        (λ* (xs) (cons (car xs) '())))
(define tail        (λ* (xs) (cdr xs)))
(define not-null?   (λ* (xs) (not (null? xs))))
(define many?       (λ* (xs) (> 1 (length xs))))
(define one?        (λ* (xs) (= 1 (length xs))))
(define filter-null (λ* (xs) (filter not-null? xs)))
(define streq?      (λ* (a b) (string=? a b)))
(define const-null  (λ* (#:rest xs) '()))
(define printf      (λ* (fmt #:rest args)
                        (apply format `(#t ,fmt ,@args))))
(define fmt         (λ* (fmt #:rest args)
                        (apply format `(#f ,fmt ,@args))))
(define errorfmt    (λ* (fmt #:rest args)
                        (error (apply fmt `(,(string-append ";; Error: " fmt)
                                            ,@args)))))

(define (list-intersperse src-l elem)
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

(define apply-cleanup
  (λ* (#:rest args)
      (when (null? args)
        (errorfmt "apply-cleanup: not enough arguments"))
      `(cleanup ',(car args)
                ,@(map (λ (x) (if (list? x) (apply-cleanup-list x) x))
                       (cdr args)))))

(define apply-cleanup-list
  (λ* (xs) (apply apply-cleanup xs)))


(define cleanup-syntax
  (λ* (stx)
      (let ((apply-cleanup
             (λ* (#:rest args)
                 (when (null? args)
                   (errorfmt "apply-cleanup: not enough arguments"))
                 `(cleanup ',(car args)
                           ,@(map (λ (x) (if (list? x) (apply-cleanup-list x) x))
                                  (cdr args)))))

            (apply-cleanup-list
             (λ* (xs) (apply apply-cleanup xs)))

            (cleanup
             (λ* (func #:rest args)
                 (let* ((fargs   (filter-null args))
                        (fst     (λ* () (car fargs)))
                        (err     (λ* (fn) (errorfmt "~s: (~s . ~s)"
                                                 fn func fargs)))
                        (to-str  (λ* () (apply string-append fargs)))
                        (to-num  (λ* ()
                                     (cond
                                      ((string? (fst)) (string->number (fst)))
                                      ((number? (fst)) (fst))
                                      (#t              (err 'to-num)))))
                        (to-sym  (λ* ()
                                     (cond
                                      ((string? (fst)) `',(string->symbol (fst)))
                                      ((symbol? (fst)) (fst))
                                      (#t              (err 'to-sym)))))
                        (to-func (λ* (fn) `(,fn ,@fargs)))
                        (to-list (to-func 'many))
                        (to-null (λ* () '()))
                        (ident   (λ* () `(,func ,@fargs))))
                   (match func
                     ('space                 " ")
                     ('newline               (to-null))
                     ('comment               (to-null))
                     ('introduce             (to-null))
                     ('keyword               (to-str))
                     ('value                 (to-str))
                     ('string                (to-str))
                     ('integer               (to-num))
                     ('float                 (to-num))
                     ('name                  (to-str))
                     ('ref                   (to-str))
                     ('match-equations       (to-list))
                     ('let-definitions       (to-list))
                     ('let-scope             (to-list))
                     ('letrec-definitions    (to-list))
                     ('letrec-scope          (to-list))
                     ('lam-var               (to-str))
                     ('lam-vars              (to-list))
                     ('lam-body              (to-list))
                     ('function              (to-str))
                     ('type-definition-vars  (to-func 'type-vars))
                     ('type-definition-cons  (to-list))
                     ('constructor-arguments (to-list))
                     (_                      (ident)))))))
        (local-eval (apply-cleanup-list stx) (the-environment)))))

(define render-syntax
  (λ* (stx)
      (letrec
          ((joining     (λ* (list between)
                            (apply string-append
                                   (list-intersperse list between))))
           (render      (λ* (x) (render-syntax x)))
           (type-vars   (λ* (vs) (joining (map render vs) ", ")))
           (type-cons   (λ* (cs) (joining (map render cs) " | ")))
           (con-args    (λ* (as) (joining (map render as) " * ")))
           (app-args    (λ* (as) (joining (map render as) " ")))

           (type        (λ* (n vs cs)
                            (fmt "type ~a ~a = ~a;;\n"
                                 (if (null? vs)
                                     ""
                                     (string-append
                                      "(" (type-vars vs) ")"))
                                 n (type-cons cs))))
           (con         (λ* (cn as)
                            (if (null? as)
                                cn
                                (fmt "~s of (~s)" cn (con-args as)))))
           (conditional (λ* (c t f)
                            (fmt "(if ~s then ~s else ~s)" c t f)))
           (match       (λ* (v es) (fmt "(match ~a with ~a)"
                                        v (joining (map render es)
                                                   " | "))))
           (match-eqn   (λ* (p v)  (fmt "~a -> ~a" p v)))
           (let-in      (λ* (es s) (fmt "(let ~a in ~a)"
                                        (joining (map render es) "; ") s)))
           (def         (λ* (es #:optional (s '()))
                            (if (null? s)
                                (fmt "let ~a;;"
                                     (joining (map render es) "; "))
                                (fmt "let ~a in ~a;;"
                                     (joining (map render es) "; ") s))))
           (let-eqn     (λ* (n v) (fmt "~a = (~a)" n v)))
           (letrec-in   (λ* (es s) (fmt "(let rec ~a in ~a)"
                                        (joining (map render es) "; ") s)))
           (defrec      (λ* (es #:optional (s '()))
                            (if (null? s)
                                (fmt "let rec ~a;;"
                                     (joining (map render es) "; "))
                                (fmt "let rec ~a in ~a;;"
                                     (joining (map render es) "; ") s))))
           (letrec-eqn  (λ* (n v) (fmt "~a = (~a)" n v)))
           (lam         (λ* (vs bd) (fmt "(fun ~a -> ~a)"
                                         (joining (map symbol->string vs) " ")
                                         (map render bd))))
           (app         (λ* (f #:rest as)
                            (if (symbol? f)
                                (fmt "(~a ~a)"
                                     (symbol->string f)
                                     (app-args as))
                                (fmt "(~a ~a)"
                                     (render f)
                                     (app-args as))))))
        (local-eval stx (the-environment)))))

(define normalize-syntax
  (λ* (stx)
      (let*
          ((inside-error          (λ* (tag xs)
                                      (errorfmt "inside: ~s | ~s" tag xs)))
           (inside                (λ* (tag) (λ* (x) x)))
           (to-func               (λ* (func)
                                      (λ* (#:rest args)
                                          `(,func ,@(map (λ (x) `',x)
                                                         (filter-null args))))))
           (to-func-n-error       (λ* ()
                                      (errorfmt
                                       "to-func-n: wrong number of arguments")))
           (to-func-n             (λ* (func n)
                                      (λ* (#:rest args)
                                          (let ((result (λ ()
                                                          (apply (to-func func)
                                                                 args)))
                                                (error  to-func-n-error))
                                            (cond
                                             ((= (length args) n) => (result))
                                             (#t                  => (error)))))))


           (body                  (λ* (#:rest xs) `'(,@xs)))
           (many                  (to-func   'many))
           (paren                 (inside    'paren))
           (name                  (inside    'name))
           (integer               (inside    'integer))
           (float                 (inside    'float))
           (string                (inside    'string))
           (conditional           (to-func-n 'conditional 3))
           (match-expression      (to-func-n 'match 2))
           (match-input           (inside    'match-input))
           (match-equation        (to-func-n 'match-eqn 2))
           (match-equation-val    (inside    'match-equation-val))
           (match-equation-pat    (inside    'match-equation-pat))
           (let-expression        (to-func-n 'let-in 2))
           (let-declaration       (λ* (d #:optional s) (if (null? s)
                                                           `(def ',d ',s)
                                                           `(def ',d))))
           (let-equation          (to-func-n 'let-eqn 2))
           (let-equation-name     (inside    'let-equation-name))
           (let-equation-val      (inside    'let-equation-val))
           (letrec-expression     (to-func-n 'letrec-in 2))
           (letrec-declaration    (λ* (d #:optional s) (if (null? s)
                                                           `(defrec ',d ',s)
                                                           `(defrec ',d))))
           (letrec-equation       (to-func-n 'letrec-eqn 2))
           (letrec-equation-name  (inside    'letrec-equation-name))
           (letrec-equation-val   (inside    'letrec-equation-val))
           (lam                   (to-func-n 'lam 2))
           (application           (to-func   'app))
           (argument              (inside    'argument))
           (type-definition       (to-func-n 'type 3))
           (type-definition-name  (inside    'type-definition-name))
           (type-definition-var   (inside    'type-definition-vars))
           (constructor           (to-func-n 'con 2))
           (constructor-name      (inside    'constructor-name))
           (constructor-argument  (inside    'constructor-argument)))
        (local-eval stx (the-environment)))))

(define prerender-syntax
  (λ* (stx)
      (letrec
          ((apply-process
            (λ* (#:rest args)
                (case (length args)
                  ((0)  (errorfmt "apply-process: not enough arguments"))
                  ((1)  (car args))
                  (else `(process ',(car args)
                                  ,@(map (λ (x) (if (list? x)
                                                    (apply-to-list x) x))
                                         (cdr args)))))))

           (apply-to-list (λ* (xs) (apply apply-process xs)))

           (render (λ (x) (render-syntax
                           (normalize-syntax x))))

           (process
            (λ* (func #:rest args)
                (let* ((to-rend (λ* () (apply string-append
                                              (map render args))))
                       (ident   (λ* () `(,func ,@args))))
                  (match func
                    ('rend                  (to-rend))
                    (_                      (ident)))))))
        (local-eval (apply-to-list stx) (the-environment)))))

(define read-xml
  (λ* (path)
      (let* ((port (open-file path "r"))
             (xml (cdr (xml->sxml port))))
        (close-port port)
        xml)))

(define main
  (λ* ()
      (catch 'exit
        (begin
          (unless (equal? (length (command-line)) 2)
            (format #t "~a\n" "Usage: normalize.scm <path-to-xml>")
            (throw 'exit 1))

          (let ((input-path (list-ref (command-line) 1)))
            (unless (access? input-path R_OK)
              (format #t "~a\n" "File not found. Quitting.")
              (throw 'exit 2))

            (let* ((input-data (car (read-xml input-path)))
                   (clean-data (cleanup-syntax input-data)))
              ;; (map (λ* (x)
              ;;        (statprof-reset 0 0 #t)
              ;;        (statprof (λ* () (pretty-print
              ;;                              (prerender-syntax x)))
              ;;                  #:count-calls? #t))
              ;;      (cdr clean-data))
              (pretty-print (cdr clean-data))
              (exit 0))))

        (λ* (key code)
            (printf "Exit code: ~s" code)
            (exit code)))))

;;(main)
(define test-func (λ (x)
                    (pretty-print (cleanup-syntax x))
                    (pretty-print (prerender-syntax (cleanup-syntax x)))))
