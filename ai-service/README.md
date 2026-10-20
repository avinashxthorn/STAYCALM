# Staycalm AI service

This directory mirrors the application-specific source of the hosted coaching service. The complete deployable source (framework starter, migrations and build configuration) is versioned in the Sites project identified in service.json. It contains no API credentials. Use the Sites source workflow to open that same project for backend changes; preserve its project ID and access policy.

Runtime secret: STAYCALM_AI_API_KEY. Non-secret runtime configuration: FIREBASE_PROJECT_ID and AI_MODEL. Configure the secret through server environment settings; never turn it into a BuildConfig value or ship it in an APK.

Every coaching request verifies a Firebase RS256 identity token, exact project issuer/audience, expiry and email verification. Responses and quota/cache records are scoped by the authenticated UID hash. The client cannot choose another account. Requests are bounded, outputs are validated, and no model response can execute a planner action. Cached reports expire after six hours for generation; old server cache records are pruned after seven days. Request attempts are capped at six per account per UTC day, at least one minute apart, and fifty shared attempts daily.

Run `npm test` on Node 22+ for token, validation, quota and credential-handling checks. Native Android UI/device and update-retention checks run in the app workflow. Live identity-token-to-provider integration still requires an authenticated application client and available provider quota.

The native Sites project starts owner-private. Mobile clients need the transport endpoint to accept network requests without ChatGPT browser sign-in; Firebase account verification remains enforced by the API. Changing the platform audience requires the owner's explicit instruction. No Sites service-access bearer token belongs in the Android app.
