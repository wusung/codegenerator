apt-get update
apt-get install -y linux-image-generic-lts-trusty
wget -qO- https://get.docker.com/gpg | sudo apt-key add -
wget -qO- https://get.docker.com/ | sh
usermod -aG docker vagrant

apt-get install -y default-jre
apt-get install -y default-jdk

apt-get install -y maven
apt-get install -y gradle

apt-get install -y python3-pip
apt-get install -y python-pip
apt-get install -y npm

pip2 install cqlsh
pip3 install cqlsh
pip install docker-compose
git clone https://github.com/dmeranda/demjson.git
cd demjson; python setup.py install

mount -t vboxsf -o uid=`id -u vagrant`,gid=`id -g vagrant`,dmode=755,fmode=744 vagrant /vagrant

