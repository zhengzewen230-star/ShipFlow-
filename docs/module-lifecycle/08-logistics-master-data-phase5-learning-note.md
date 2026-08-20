# Fifth Phase Learning Note

## Runtime mapper contract

A MyBatis SQL fragment is part of a mapper method's parameter contract. A detail method accepting only `channelId` must not include a list-filter fragment that evaluates `channelCode`, `channelName`, `serviceCountry`, or `status`; otherwise the request fails before SQL execution. Keep base visibility predicates separate from optional list filters and test the detail statement directly.

## Spring constructor selection

When a service gains a second constructor for an additional dependency, Spring needs one unambiguous constructor. Mark the production constructor with `@Autowired` when retaining an overload for tests or compatibility. Verify the full application context starts after targeted unit and WebMvc tests pass.

## Data boundary

The mapping and injection fixes require no database migration or business write. V021/V022, store-resource data, warehouse master data, and browser write acceptance remain separately gated.

## OpenAPI inventory

Operation counts are contract evidence, not a target to preserve mechanically. When a tenant route would return a complete platform price rule with tiers, remove the route and its stale service/mapper entry, then record the exact operation ID, method, path, replacement projection, and resulting count. Do not restore it solely to reach an earlier count.
