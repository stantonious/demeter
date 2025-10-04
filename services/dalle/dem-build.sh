#!/usr/bin/bash

set -e
set -x

rm -rf lib/ dist/ 
mkdir lib 
python3 setup.py sdist 

packages=("utils" )
for i in "${packages[@]}"
do
    pushd ../${i}/
    python3 setup.py sdist
    popd
    pip3 install ../${i}/dist/*gz -t lib/
done
pip install -t lib dist/*.gz 
touch lib/__init__.py 
gcloud app deploy --version=1
