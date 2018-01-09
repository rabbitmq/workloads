.ONESHELL:# single shell invocation for all lines in the recipe
SHELL = bash# we depend on bash expansion for e.g. queue patterns

.DEFAULT_GOAL = help

### TARGETS ###

ifndef SECURE
delete_queues: SSL_CONNS = --insecure
endif
delete_queues: VHOST ?= %2F
delete_queues: _require_api_url _require_queue_pattern ## Delete multiple queues - required: API_URL, QUEUE_PATTERN - optional: VHOST, CURL_ARGS, SECURE
	@curl --silent --verbose $(SSL_CONNS) --request DELETE $(CURL_ARGS)\
	  $(API_URL)/api/queues/$(VHOST)/$(QUEUE_PATTERN)

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

_require_api_url:
ifndef API_URL
	$(error API_URL make variable must be set, remember to include a USER & PASS if required, e.g. https://USER:PASS@lqs-ha-confirm-multiack-3-6-14.gcp.rabbitmq.com/)
endif

_require_queue_pattern:
ifndef QUEUE_PATTERN
	$(error QUEUE_PATTERN make variable must be set, remember to use single quotes, e.g. 'perf-test-{1..10}')
endif
