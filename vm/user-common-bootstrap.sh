#!/bin/bash

# Print script commands and exit on errors.
set -xe

# --- Mininet --- #
MININET_COMMIT="aa0176fce6fb718a03474f8719261b07b670d30d"  # 2022-Apr-02
git clone https://github.com/mininet/mininet mininet
cd mininet
git checkout ${MININET_COMMIT}
PATCH_DIR="${HOME}/patches"
patch -p1 < "${PATCH_DIR}/mininet-dont-install-python2-2022-apr.patch" || echo "Errors while attempting to patch mininet, but continuing anyway ..."
cd ..
# TBD: Try without installing openvswitch, i.e. no '-v' option, to see
# if everything still works well without it.
sudo ./mininet/util/install.sh -nw

find /usr/lib /usr/local $HOME/.local | sort > $HOME/usr-local-7-after-mininet-install.txt

# Move NEST to VM
sudo chown -R NEST:NEST nest
sudo mv nest /home/NEST
sudo rm -rf nest

# Move examples to VM
sudo chown -R NEST:NEST examples
sudo mv examples /home/NEST
sudo rm -rf examples

# Move testing files to VM
sudo chown -R NEST:NEST testing
sudo mv testing /home/NEST
sudo rm -rf testing

# Move utils to VM
sudo chown -R NEST:NEST utils
sudo mv utils /home/NEST
sudo rm -f utils

# Move makefile to VM
sudo chown -R NEST:NEST Makefile
sudo mv Makefile /home/NEST
sudo rm -f Makefile

sudo mkdir /home/NEST/.testdata
sudo chown -R NEST:NEST /home/NEST/.testdata
sudo mkdir /home/NEST/.example_logs
sudo chown -R NEST:NEST /home/NEST/.example_logs
sudo mkdir /home/NEST/plots
sudo chown -R NEST:NEST /home/NEST/plots

# --- Emacs --- #
sudo cp p4_16-mode.el /usr/share/emacs/site-lisp/
sudo mkdir /home/NEST/.emacs.d/
echo "(autoload 'p4_16-mode' \"p4_16-mode.el\" \"P4 Syntax.\" t)" > init.el
echo "(add-to-list 'auto-mode-alist '(\"\\.p4\\'\" . p4_16-mode))" | tee -a init.el
sudo mv init.el /home/NEST/.emacs.d/
sudo ln -s /usr/share/emacs/site-lisp/p4_16-mode.el /home/NEST/.emacs.d/p4_16-mode.el
sudo chown -R NEST:NEST /home/NEST/.emacs.d/

# --- Vim --- #
cd ~
mkdir .vim
cd .vim
mkdir ftdetect
mkdir syntax
echo "au BufRead,BufNewFile *.p4      set filetype=p4" >> ftdetect/p4.vim
echo "set bg=dark" >> ~/.vimrc
sudo mv ~/.vimrc /home/NEST/.vimrc
cp ~/p4.vim syntax/p4.vim
cd ~
sudo mv .vim /home/NEST/.vim
sudo chown -R NEST:NEST /home/NEST/.vim
sudo chown NEST:NEST /home/NEST/.vimrc

# --- Adding Desktop icons --- #
DESKTOP=/home/${USER}/Desktop
mkdir -p ${DESKTOP}

cat > ${DESKTOP}/Terminal.desktop << EOF
[Desktop Entry]
Encoding=UTF-8
Type=Application
Name=Terminal
Name[en_US]=Terminal
Icon=konsole
Exec=/usr/bin/x-terminal-emulator
Comment[en_US]=
EOF

cat > ${DESKTOP}/Wireshark.desktop << EOF
[Desktop Entry]
Encoding=UTF-8
Type=Application
Name=Wireshark
Name[en_US]=Wireshark
Icon=wireshark
Exec=/usr/bin/wireshark
Comment[en_US]=
EOF

sudo mkdir -p /home/NEST/Desktop
sudo mv /home/${USER}/Desktop/* /home/NEST/Desktop
sudo chown -R NEST:NEST /home/NEST/Desktop/

# Do this last!
sudo reboot
