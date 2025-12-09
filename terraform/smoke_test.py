
import requests
import sys

# ALB DNS Name
ALB_URL = "http://unisync-alb-531510619.ap-northeast-2.elb.amazonaws.com"

# Health Check & Documentation Endpoints
# Using /v3/api-docs/{service} to verify Gateway -> Service connectivity
endpoints = [
    ("/actuator/health", "API Gateway Health"),
    ("/v3/api-docs/user-service", "User Service Docs"),
    ("/v3/api-docs/course-service", "Course Service Docs"),
    ("/v3/api-docs/schedule-service", "Schedule Service Docs")
]

print(f"Starting Connectivity Smoke Test against {ALB_URL}...\n")

failed = False

for path, name in endpoints:
    url = f"{ALB_URL}{path}"
    try:
        print(f"Testing {name} ({path})...", end=" ")
        resp = requests.get(url, timeout=10)
        
        if resp.status_code == 200:
            print("✅ OK")
            # print(f"   Response: {resp.text[:50]}...")
        else:
            print(f"❌ FAILED (Status: {resp.status_code})")
            print(f"   Response: {resp.text[:200]}")
            failed = True
            
    except Exception as e:
        print(f"❌ ERROR: {e}")
        failed = True

if failed:
    print("\nSome tests failed. Check ALB rules or Gateway configuration.")
    sys.exit(1)
else:
    print("\nAll systems operational and reachable via API Gateway! 🚀")
    sys.exit(0)
