#!/bin/bash

BASE_URL="http://localhost:8080/weather"

# Function to convert the API JSON to CSV using Python (built-in on Ubuntu)
json_to_csv() {
    local json_data="$1"
    local output_file="$2"
    echo "key,value" > "$output_file"
    echo "$json_data" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    for k, v in data.items():
        # Clean value string to ensure it stays on one line in CSV
        val_str = json.dumps(v).replace('\"', '\"\"')
        print(f'{k},\"{val_str}\"')
except Exception as e:
    sys.exit(1)
" >> "$output_file"
}

case "$1" in
    --view-all)
        timestamp=$(date +%s)
        filename="${timestamp}.csv"
        echo "Fetching all records..."
        response=$(curl -s "$BASE_URL/all")
        json_to_csv "$response" "$filename"
        echo "Latest values saved to $filename"
        ;;

    --view)
        # Extract key from --key=SOME_KEY
        if [[ $2 == --key=* ]]; then
            key="${2#*=}"
            echo "Fetching value for key: $key"
            response=$(curl -s "$BASE_URL?stationId=$key")
            echo "$response"
        else
            echo "Usage: ./bitcask_client.sh --view --key=SOME_KEY"
        fi
        ;;

    --perf)
        # Extract client count from --clients=100
        if [[ $2 == --clients=* ]]; then
            clients="${2#*=}"
            timestamp=$(date +%s)
            echo "Starting $clients threads for performance test..."
            
            for i in $(seq 1 $clients); do
                (
                    response=$(curl -s "$BASE_URL/all")
                    filename="${timestamp}_thread_${i}.csv"
                    json_to_csv "$response" "$filename"
                ) &
            done
            wait
            echo "Performance test complete. Generated $clients files."
        else
            echo "Usage: ./bitcask_client.sh --perf --clients=100"
        fi
        ;;

    *)
        echo "Usage:"
        echo "  ./bitcask_client.sh --view-all"
        echo "  ./bitcask_client.sh --view --key=SOME_KEY"
        echo "  ./bitcask_client.sh --perf --clients=100"
        ;;
esac
