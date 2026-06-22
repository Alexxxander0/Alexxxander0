import yaml
try:
    with open('src/main/resources/lang.yml', 'r') as file:
        docs = yaml.safe_load(file)
        print("YAML parsed successfully")
except Exception as e:
    print(f"Error: {e}")
