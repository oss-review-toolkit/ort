from setuptools import setup, find_packages

setup(
    name='Example-App',
    description='A synthetic test case for OSS Review Toolkit',
    version='2.4.0',
    url='https://example.org/app',
    license='MIT License',
    classifiers=[
        'License :: OSI Approved :: MIT License'
    ],
    install_requires=['Flask>=0.12, <1.1'],
    packages=find_packages(),
)
