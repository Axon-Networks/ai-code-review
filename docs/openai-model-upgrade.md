# Modern OpenAI model support

The stateless ChatGPT client uses the Responses API for GPT-5 and GPT-6 model
families, including versioned model IDs. GPT-6.1 Sol is the default for stateless
ChatGPT reviews. Explicit older models and other providers retain their existing
protocols; legacy stateful and generic configurations retain the GPT-4o default.

GPT-6.1 Sol and GPT-6 Astra require Responses for function calling. Requests reuse
the existing review prompts and `format_replies` schema, flatten the function
definition, explicitly disable strict schema normalization, force that function,
disable parallel calls, and set `store:false`. They omit unsupported temperature
and seed parameters and use the model's default reasoning effort. Output limits
retain their existing provider-specific behavior.

Only completed responses containing valid `format_replies` arguments become
Gerrit review data. Streamed requests use the final `response.completed` event,
which contains the complete output. Failed, incomplete, malformed, or interrupted
responses fail before comments or votes are posted. Existing change-ID validation
and retry behavior continue to apply.

The legacy stateful client uses Assistants and vector stores. Modern models are
rejected during event setup with an actionable instruction to select
`aiMode=stateless`, before constructing that client. Model validation also checks
this compatibility. Modern stateless merge events skip Assistants and vector-store
maintenance. Migrating the stateful persistence and retrieval workflow is outside
this change.

Validation covers request serialization and response decoding, model/provider
routing and defaults, and loopback HTTP review/comment flows through the real
stateless client with Gerrit API test doubles. The existing legacy and Anthropic
tests remain part of the full Maven build. Integration disposition: **Required**,
because the OpenAI HTTP contract changes.

Verified on 2026-10-11 with JDK 21 and `mvn clean package`: 152 tests passed,
zero failures or errors, and nine pre-existing skipped conversational tests.
This includes 16 loopback modern-model integration tests, 36 Responses adapter
tests, and 14 configuration/event compatibility tests. The build applied the
repository's Java formatter and produced `target/ai-code-review-3.14.0.jar`.
Live OpenAI account access and deployment were not exercised.

Sources:

- [GPT-6 migration](https://developers.openai.com/api/docs/guides/latest-model/gpt-6-astra.md#migration-quickstart)
- [Responses migration](https://developers.openai.com/api/docs/guides/migrate-to-responses)
- [Function calling](https://developers.openai.com/api/docs/guides/function-calling#strict-mode)
- [Streaming Responses](https://developers.openai.com/api/docs/guides/streaming-responses)
