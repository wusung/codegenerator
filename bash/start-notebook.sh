cd ~/workspace/kyper-core/docker-builds/flyberry/
export IPYTHONDIR=~/workspace/kyper-core/docker-builds/flyberry/ipython-frontend
ipython notebook --port=8888 --profile=nbserver --log-level=DEBUG
