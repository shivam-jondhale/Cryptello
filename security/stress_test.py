import requests
import concurrent.futures
import time
import random
import uuid

# Configuration
BASE_URL = "http://localhost:5454"
NUM_REQUESTS = 100  # Total requests to send
CONCURRENCY = 10    # Simultaneous threads

endpoints = [
    {
        "name": "Auth Signin (Bad Creds)",
        "method": "POST",
        "url": "/api/auth/signin",
        "json": {"email": "attacker@example.com", "password": "wrongpassword"}
    },
    {
        "name": "Stripe Webhook (Random Junk)",
        "method": "POST",
        "url": "/api/webhooks/stripe",
        "headers": {"Stripe-Signature": "t=" + str(int(time.time())) + ",v1=invalid_signature"},
        "data": "junk_payload_" + str(uuid.uuid4())
    },
    # Add public endpoint if available, e.g. market data
    # {
    #     "name": "Public Market Data",
    #     "method": "GET",
    #     "url": "/api/coins" 
    # }
]

def send_request(endpoint):
    start_time = time.time()
    try:
        if endpoint["method"] == "POST":
            # Handle JSON vs Data
            if "json" in endpoint:
                response = requests.post(BASE_URL + endpoint["url"], json=endpoint["json"], headers=endpoint.get("headers"))
            else:
                response = requests.post(BASE_URL + endpoint["url"], data=endpoint["data"], headers=endpoint.get("headers"))
        else:
            response = requests.get(BASE_URL + endpoint["url"])
        
        elapsed = time.time() - start_time
        return response.status_code, elapsed, endpoint["name"]
    except Exception as e:
        return "ERR", 0, endpoint["name"]

def run_stress_test():
    print(f"Starting Stress Test: {NUM_REQUESTS} requests with {CONCURRENCY} threads...")
    
    results = {200: 0, 429: 0, 401: 0, 400: 0, "ERR": 0, "OTHER": 0}
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENCY) as executor:
        futures = []
        for _ in range(NUM_REQUESTS):
            endpoint = random.choice(endpoints)
            futures.append(executor.submit(send_request, endpoint))
        
        for future in concurrent.futures.as_completed(futures):
            status, elapsed, name = future.result()
            
            if status == 429:
                results[429] += 1
                print(f"[{name}] 429 Rate Limited ({elapsed:.2f}s)")
            elif status == 200:
                results[200] += 1
                # print(f"[{name}] 200 OK ({elapsed:.2f}s)")
            elif status == 401:
                results[401] += 1
            elif status == 400:
                results[400] += 1
            elif status == "ERR":
                results["ERR"] += 1
                print(f"[{name}] Connection Error")
            else:
                results["OTHER"] += 1
                print(f"[{name}] Status {status} ({elapsed:.2f}s)")

    print("\n--- Results ---")
    print(f"Total Requests: {NUM_REQUESTS}")
    print(f"200 OK: {results[200]}")
    print(f"429 Too Many Requests: {results[429]}")
    print(f"401 Unauthorized: {results[401]}")
    print(f"400 Bad Request: {results[400]}")
    print(f"Errors: {results['ERR']}")
    print(f"Other: {results['OTHER']}")

if __name__ == "__main__":
    run_stress_test()
