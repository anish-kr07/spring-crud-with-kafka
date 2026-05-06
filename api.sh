#!/usr/bin/env bash
# Curl recipes for every /v1/employees endpoint.
#
# Usage:
#   ./api.sh <command> [args]
# Examples:
#   ./api.sh list
#   ./api.sh get 1
#   ./api.sh search ada@example.com 100000
#   ./api.sh create
#   ./api.sh put 1
#   ./api.sh patch 1 '{"salary":160000}'
#   ./api.sh delete 1
#
# Or just copy/paste any of the curl blocks below directly into your shell.

BASE="${BASE:-http://localhost:8080}"
AUTH="${AUTH:-user:password}"
CURL="curl -sS -i -u $AUTH"

case "${1:-help}" in

  # ---------------------------------------------------------------------------
  # GET /v1/employees                          -> 200, list of all employees
  # ---------------------------------------------------------------------------
  list)
    $CURL "$BASE/v1/employees"
    ;;

  # ---------------------------------------------------------------------------
  # GET /v1/employees/{id}                     -> 200 + DTO, or 404
  # ---------------------------------------------------------------------------
  get)
    id="${2:?usage: ./api.sh get <id>}"
    $CURL "$BASE/v1/employees/$id"
    ;;

  # ---------------------------------------------------------------------------
  # GET /v1/employees/search?email=&minSalary= -> 200 + (possibly empty) list
  # ---------------------------------------------------------------------------
  search)
    email="${2:?usage: ./api.sh search <email> [minSalary]}"
    min="${3:-0}"
    $CURL "$BASE/v1/employees/search?email=$email&minSalary=$min"
    ;;

  # ---------------------------------------------------------------------------
  # POST /v1/employees                         -> 201 + DTO + Location header
  # Pass an optional departmentId as $2 (defaults to 1).
  # ---------------------------------------------------------------------------
  create)
    deptId="${2:-1}"
    $CURL -X POST "$BASE/v1/employees" \
      -H "Content-Type: application/json" \
      -d '{
        "firstName":"Hedy",
        "lastName":"Lamarr",
        "email":"hedy-'"$RANDOM"'@example.com",
        "departmentId":'"$deptId"',
        "salary":140000,
        "joinDate":"2024-02-01",
        "dateOfBirth":"1984-11-14"
      }'
    ;;

  # ---------------------------------------------------------------------------
  # PUT /v1/employees/{id}                     -> 200 + DTO (full replace)
  # All fields required — partial body fails validation.
  # ---------------------------------------------------------------------------
  put)
    id="${2:?usage: ./api.sh put <id> [departmentId]}"
    deptId="${3:-2}"
    $CURL -X PUT "$BASE/v1/employees/$id" \
      -H "Content-Type: application/json" \
      -d '{
        "firstName":"Hedy",
        "lastName":"Lamarr",
        "email":"hedy-put-'"$RANDOM"'@example.com",
        "departmentId":'"$deptId"',
        "salary":150000,
        "joinDate":"2024-02-01",
        "dateOfBirth":"1984-11-14"
      }'
    ;;

  # ---------------------------------------------------------------------------
  # PATCH /v1/employees/{id}                   -> 200 + DTO (partial update)
  # Pass a JSON body as $3, or omit to use a default salary-only patch.
  # ---------------------------------------------------------------------------
  patch)
    id="${2:?usage: ./api.sh patch <id> [json]}"
    body="${3:-{\"salary\":160000\}}"
    $CURL -X PATCH "$BASE/v1/employees/$id" \
      -H "Content-Type: application/json" \
      -d "$body"
    ;;

  # ---------------------------------------------------------------------------
  # DELETE /v1/employees/{id}                  -> 204 No Content, or 404
  # ---------------------------------------------------------------------------
  delete)
    id="${2:?usage: ./api.sh delete <id>}"
    $CURL -X DELETE "$BASE/v1/employees/$id"
    ;;

  # ===========================================================================
  # /v1/departments
  # ===========================================================================

  # GET /v1/departments — list departments with their employees nested.
  # Watch the SQL log: with @EntityGraph in the repo it's ONE query.
  dept-list)
    $CURL "$BASE/v1/departments"
    ;;

  # GET /v1/departments/{id}
  dept-get)
    id="${2:?usage: ./api.sh dept-get <id>}"
    $CURL "$BASE/v1/departments/$id"
    ;;

  # POST /v1/departments — create a new department.
  dept-create)
    name="${2:-Marketing-$RANDOM}"
    $CURL -X POST "$BASE/v1/departments" \
      -H "Content-Type: application/json" \
      -d '{"name":"'"$name"'"}'
    ;;

  # ===========================================================================
  # Error-path probes — each one targets a specific @ExceptionHandler in
  # GlobalExceptionHandler. Expect Content-Type: application/problem+json.
  # ===========================================================================

  # 404 — NotFoundException
  err-404)
    $CURL "$BASE/v1/employees/99999"
    ;;

  # 400 — HttpMessageNotReadableException
  # Truncated JSON: Jackson can't parse it. Should NOT leak a stack trace.
  err-bad-json)
    $CURL -X POST "$BASE/v1/employees" \
      -H "Content-Type: application/json" \
      -d '{"firstName":'
    ;;

  # 400 — HttpMessageNotReadableException via unparseable date.
  # "not-a-date" is clearly not a LocalDate, so Jackson rejects it.
  # (Using a wrong-format-but-real-looking date like "1984-11-14" parses fine
  # under the global yyyy-MM-dd, so it wouldn't trigger this handler.)
  err-bad-date)
    $CURL -X POST "$BASE/v1/employees" \
      -H "Content-Type: application/json" \
      -d '{
        "firstName":"Bad",
        "lastName":"Date",
        "email":"bad-date-'"$RANDOM"'@example.com",
        "departmentId":1,
        "salary":1,
        "joinDate":"2024-02-01",
        "dateOfBirth":"not-a-date"
      }'
    ;;

  # 409 — DataIntegrityViolationException (duplicate email).
  # Per-run random email so the FIRST call always succeeds (201) and the SECOND
  # always collides (409). Without randomness, an existing row from a previous
  # run would make the first call also 409 and obscure the test.
  err-409)
    email="dup-${RANDOM}-${RANDOM}@example.com"
    body='{
      "firstName":"Dup",
      "lastName":"Email",
      "email":"'"$email"'",
      "departmentId":1,
      "salary":1,
      "joinDate":"2024-02-01",
      "dateOfBirth":"1984-11-14"
    }'
    echo "--- 1st call (expect 201) ---"
    $CURL -X POST "$BASE/v1/employees" -H "Content-Type: application/json" -d "$body"
    echo
    echo "--- 2nd call same email (expect 409) ---"
    $CURL -X POST "$BASE/v1/employees" -H "Content-Type: application/json" -d "$body"
    ;;

  # 400 — MethodArgumentNotValidException (you'll write this handler).
  # Empty firstName, bad email, negative salary, missing date fields all violate
  # @NotBlank / @Email / @Positive / @NotNull on EmployeeRequest.
  err-validation)
    $CURL -X POST "$BASE/v1/employees" \
      -H "Content-Type: application/json" \
      -d '{"firstName":"","email":"not-an-email","salary":-5}'
    ;;

  # Run every error probe back-to-back. Use this to confirm every handler
  # fires after a code change. Look for "handler" field in each response body.
  verify)
    for cmd in err-404 err-bad-json err-bad-date err-409 err-validation; do
      echo "===================================================================="
      echo " $cmd"
      echo "===================================================================="
      "$0" "$cmd"
      echo
      echo
    done
    ;;

  help|*)
    cat <<'EOF'
Commands:
  list                       GET    /v1/employees
  get <id>                   GET    /v1/employees/{id}
  search <email> [minSalary] GET    /v1/employees/search?email=&minSalary=
  create [deptId]            POST   /v1/employees       (random email, deptId default 1)
  put <id> [deptId]          PUT    /v1/employees/{id}  (full replace)
  patch <id> [json]          PATCH  /v1/employees/{id}  (partial update)
  delete <id>                DELETE /v1/employees/{id}

Departments:
  dept-list                  GET    /v1/departments     (with employees nested)
  dept-get <id>              GET    /v1/departments/{id}
  dept-create [name]         POST   /v1/departments

Error-path probes (hit specific @ExceptionHandler):
  err-404                    GET unknown id           -> 404 NotFoundException
  err-bad-json               truncated POST body      -> 400 HttpMessageNotReadable
  err-bad-date               wrong DOB format         -> 400 HttpMessageNotReadable
  err-409                    duplicate email twice    -> 409 DataIntegrityViolation
  err-validation             empty/invalid fields     -> 400 MethodArgumentNotValid
  verify                     run every error probe in sequence

Env overrides:
  BASE=http://host:port    (default: http://localhost:8080)
  AUTH=user:pass           (default: user:password)
EOF
    ;;
esac
