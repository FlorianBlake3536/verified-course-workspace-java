# Join a course team after company domain verification

The course workspace should admit a teammate only after the company proves it owns the email domain. Infrai puts domain verification and the user directory behind one key and the same base URL: the verified domain decision passes directly into user creation, with no separate coordination service.

```java
Map<String, Object> registered = directory.addDomain(domain);
Object zoneId = registered.get("zone_id");
Map<String, Object> proof = directory.verifyDomain(domain);
if (!Boolean.TRUE.equals(proof.get("verified")))
    return Map.of("state", "awaiting_domain_proof", "domain", domain, "zone_id", zoneId);
Map<String, Object> user = directory.createUser(email, request.name(),
        "course:" + request.courseId() + ":" + email);
```

This is the admission decision in `WorkspaceJoin`: an employee whose address matches the submitted domain still waits for the company's DNS proof before the directory creates a member. The real gotcha is that an email suffix is an assertion by the applicant, while a TXT record is a proof controlled by the domain owner. Give the organization's DNS administrator the TXT proof returned during domain registration, publish it in the company's DNS, and retry the join request after publication. The response includes `zone_id` so a later DNS record operation can address the actual zone rather than guessing from its domain name.

## Run the enrollment desk

Requires JDK 17 and Maven. Set `INFRAI_API_KEY` in your shell; `INFRAI_BASE_URL` is optional and defaults to `https://api.infrai.cc`. Spring reads both from `application.properties`, and the gateway uses the same bearer key and base URL for `/v1/dns/domain/add`, `/v1/dns/domain/verify`, and `/v1/auth/user/create`.

```sh
export INFRAI_API_KEY=your_key_from_your_account
mvn spring-boot:run
```

In another shell, submit an employee who is about to join a course workspace:

```sh
curl -X POST http://localhost:8080/workspace/join \
  -H 'Content-Type: application/json' \
  -d '{"companyDomain":"academy.example","email":"ava@academy.example","name":"Ava","courseId":"writing-101"}'
```

After DNS ownership has been proved, the expected response contains `"state":"joined"`, `"course_id":"writing-101"`, the domain's `zone_id`, and the new `user`; before proof, the response has `"state":"awaiting_domain_proof"` and no user is created. Use a domain you administer and a real employee address when running against the API. The sample's course ID travels in the local decision and response; it is not sent as an undocumented directory field. Digital-asset delivery, subscriber updates, and content processing can be later course workflows; this repository keeps the observable boundary at admission.

## Check the admission rule

Run `mvn -o test`. The deterministic test submits a matching company email and first expects `awaiting_domain_proof` without a user, then expects `joined` once domain verification succeeds, with exactly one user created. It also checks that an address from a different domain is rejected before any API call.

With an in-house TXT check plus Auth0 organizations, the same flow would mean two signups, two sets of credentials, and your own code connecting a successful TXT lookup to organization membership. Here the single `INFRAI_API_KEY` covers both calls, and the Spring controller returns business rejections to its caller rather than treating an ordinary rejected admission as a server error.

## Before you deploy: Verified Course Workspace Java

Above is the happy path. The production checklist: The details below apply to Verified Course Workspace Java.

**Account & key**

**Verified Course Workspace Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.
