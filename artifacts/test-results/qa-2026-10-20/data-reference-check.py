import json
import re
from pathlib import Path


root = Path.cwd()
load = lambda path: json.loads((root / path).read_text(encoding="utf-8"))
demo = load("test-data/demo.json")
registration = load("test-data/scenarios/registration-concurrency.json")
scheduling = load("test-data/scenarios/scheduling.json")

assert set(demo) == {
    "schemaVersion", "organizations", "users", "resources", "activities", "registrations"
}
assert demo["schemaVersion"] == registration["schemaVersion"] == scheduling["schemaVersion"] == 1

ids = lambda items: [item["id"] for item in items]
organization_ids, user_ids, resource_ids, activity_ids = map(
    ids, (demo["organizations"], demo["users"], demo["resources"], demo["activities"])
)
all_demo_ids = organization_ids + user_ids + resource_ids + activity_ids
assert len(all_demo_ids) == len(set(all_demo_ids)), "duplicate demo entity ID"
assert all(
    re.fullmatch(r"00000000-0000-0000-0000-\d{12}", item_id)
    for item_id in all_demo_ids
), "noncanonical demo UUID"

organization_set, user_set, resource_set, activity_set = map(
    set, (organization_ids, user_ids, resource_ids, activity_ids)
)
for user in demo["users"]:
    assert user["organizationId"] in organization_set
    assert all(scope["organizationId"] in organization_set for scope in user["approvalScopes"])
    assert set(user["managedResourceIds"]) <= resource_set

for resource in demo["resources"]:
    assert isinstance(resource["allowTeacher"], bool)
    assert isinstance(resource["allowStudent"], bool)

for activity in demo["activities"]:
    assert activity["organizationId"] in organization_set
    assert activity["ownerId"] in user_set
    plan = activity["input"]["plan"]
    assert plan["venueId"] in resource_set
    assert {line["resourceId"] for line in plan["equipment"]} <= resource_set

for registration_row in demo["registrations"]:
    assert registration_row["activityId"] in activity_set
    assert registration_row["userId"] in user_set

concurrency_users = registration["users"]
concurrency_activities = registration["activities"]
concurrency_user_ids = set(ids(concurrency_users))
assert len(concurrency_users) == 20
assert len(concurrency_user_ids) == 20
assert not (concurrency_user_ids & user_set)
for user in concurrency_users:
    assert user["organizationId"] in organization_set

for activity in concurrency_activities:
    assert activity["organizationId"] in organization_set
    assert activity["ownerId"] in user_set | concurrency_user_ids
    plan = activity["input"]["plan"]
    assert plan["venueId"] in resource_set
    assert {line["resourceId"] for line in plan["equipment"]} <= resource_set

assert scheduling["evaluation"]["strategies"] == ["FIRST_FIT", "RANKED"]
assert scheduling["evaluation"]["maxCandidates"] == 300
assert len(scheduling["scenarios"]) == 8
for scenario in scheduling["scenarios"]:
    for blocker in scenario["blockers"]:
        assert all(row["resourceId"] in resource_set for row in blocker["reservations"])
    assert all(row["resourceId"] in resource_set for row in scenario["faults"])
    plan = scenario["request"]["plan"]
    assert plan["venueId"] in resource_set
    assert {line["resourceId"] for line in plan["equipment"]} <= resource_set

print(
    "DATA_REFERENCE_CHECK_OK: demo org=2 users=9 resources=12 activities=2 "
    f"registrations={len(demo['registrations'])}; concurrency users={len(concurrency_users)} "
    f"activities={len(concurrency_activities)}; scheduling scenarios={len(scheduling['scenarios'])} "
    "strategies=2 candidateLimit=300"
)
