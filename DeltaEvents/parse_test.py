import json
import sys

def parse_yaml():
    import yaml
    try:
        with open('src/main/resources/lang.yml', 'r') as file:
            yaml.safe_load(file)
            print("YAML parsed successfully!")
    except Exception as e:
        print(f"Error parsing YAML: {e}")

try:
    import yaml
    parse_yaml()
except ImportError:
    print("PyYAML not installed, installing...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pyyaml"])
    parse_yaml()
