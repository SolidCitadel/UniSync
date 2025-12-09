
import requests

url = "http://unisync-alb-531510619.ap-northeast-2.elb.amazonaws.com/v3/api-docs/user-service"
print(f"Requesting {url}...")
try:
    resp = requests.get(url, timeout=10)
    print(f"Status: {resp.status_code}")
    print("Body:")
    print(resp.text)
except Exception as e:
    print(e)
