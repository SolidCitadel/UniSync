from setuptools import setup, find_packages

setup(
    name="unisync-shared",
    version="1.0.0",
    packages=find_packages(),
    install_requires=[
        "pydantic>=2.0.0",
    ],
    python_requires=">=3.11",
    description="Shared DTOs for UniSync microservices and serverless functions",
    author="UniSync Team",
)