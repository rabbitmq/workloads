.ONESHELL: # single shell invocation for all lines in a rule

### CONFIG ###
#
VHOST ?= %2F

### RULES ###
#
default: help

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

require_api_url:
ifndef API_URL
	$(error API_URL make variable must be set, remember to include a USER & PASS if required, e.g. https://USER:PASS@lqs-ha-confirm-multiack-3-6-14.gcp.rabbitmq.com/)
endif

require_queue_pattern:
ifndef QUEUE_PATTERN
	$(error QUEUE_PATTERN make variable must be set, remember to use single quotes, e.g. 'perf-test-{1..10}')
endif

trust_self_signed_certs:
ifndef SECURE
SSL_CONNS = --insecure
endif

delete_queues: require_api_url require_queue_pattern trust_self_signed_certs ## Delete multiple queues
	@curl --silent --verbose $(SSL_CONNS) --request DELETE $(CURL_ARGS)\
	  $(API_URL)/api/queues/$(VHOST)/$(QUEUE_PATTERN)
