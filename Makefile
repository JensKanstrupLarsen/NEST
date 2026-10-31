# make test_single EX=store FLAGS="--protocol TCP --monitored --tamper" GATHER=gatherdataTCP.sh DATA="tcp mon good"

BMV2_SWITCH_EXE = simple_switch_grpc

clear_logs:
	sudo rm -rf logs

build_clean:
	sudo rm -rf build
	sudo rm -rf logs

enable_loss:
	iptables -A INPUT -m statistic --mode random --probability 0.01 -j DROP
	sudo tc qdisc change dev eth0 root netem duplicate 1%
	sudo tc qdisc change dev eth0 root netem delay 50ms 50ms

build_udp: build_clean
	mkdir build
	mkdir logs
	p4c-bm2-ss --p4v 16 --p4runtime-files build/monitor.p4.p4info.txt,build/monitor.p4.p4info.json -o build/monitor.json nest/nest_p4/udp_monitor.p4

build_tcp: build_clean
	mkdir build
	mkdir logs
	p4c-bm2-ss --p4v 16 --p4runtime-files build/monitor.p4.p4info.txt,build/monitor.p4.p4info.json -o build/monitor.json nest/nest_p4/tcp_monitor.p4

build_basic: build_clean
	mkdir build
	mkdir logs
	p4c-bm2-ss --p4v 16 --p4runtime-files build/monitor.p4.p4info.txt,build/monitor.p4.p4info.json -o build/monitor.json nest/nest_p4/basic_router.p4

test_single:
	rm -rf examples/$(EX)/log; mkdir examples/$(EX)/log; sudo mn -c -v critical
	sudo python3 utils/run_exercise.py -t examples/$(EX)/topology.json -b simple_switch_grpc --example $(EX) $(FLAGS) -q
	sh testing/$(GATHER) $(EX) $(DATA) $(NUM)

test_multiple:
	number=1 ; while [ $$number -le $(RUNS) ] ; do \
		make test_single NUM=$$number ; \
		number=`expr $$number + 1` ; \
	done

test_example:
	rm -f .example_logs/$(EX)*
	make clean
	make build_basic
	sh testing/set_udp.sh
	make test_multiple EX=$(EX) FLAGS="--protocol UDP" GATHER=gatherdataUDP.sh DATA="udp nm good"
	make test_multiple EX=$(EX) FLAGS="--protocol UDP --faulty" GATHER=gatherdataUDP.sh DATA="udp nm bad"

	sh testing/set_tcp_no_monitors.sh
	make test_multiple EX=$(EX) FLAGS="--protocol TCP" GATHER=gatherdataTCP.sh DATA="tcp nm good"
	make test_multiple EX=$(EX) FLAGS="--protocol TCP --faulty" GATHER=gatherdataTCP.sh DATA="tcp nm bad"
	make test_multiple EX=$(EX) FLAGS="--protocol TCP --enable-loss" GATHER=gatherdataTCP.sh DATA="tcploss nm good"
	make test_multiple EX=$(EX) FLAGS="--protocol TCP --enable-loss --faulty" GATHER=gatherdataTCP.sh DATA="tcploss nm bad"

	make clean
	make build_udp
	sh testing/set_udp.sh
	make test_multiple EX=$(EX) FLAGS="--protocol UDP --monitored" GATHER=gatherdataUDP.sh DATA="udp mon good"
	make test_multiple EX=$(EX) FLAGS="--protocol UDP --monitored --faulty" GATHER=gatherdataUDP.sh DATA="udp mon bad"

	make clean
	make build_tcp
	sh testing/set_tcp.sh
	make test_multiple EX=$(EX) FLAGS="--protocol TCP --monitored" GATHER=gatherdataTCP.sh DATA="tcp mon good"
	make test_multiple EX=$(EX) FLAGS="--protocol TCP --monitored --faulty" GATHER=gatherdataTCP.sh DATA="tcp mon bad"
	make test_multiple EX=$(EX) FLAGS="--protocol TCP --monitored --enable-loss" GATHER=gatherdataTCP.sh DATA="tcploss mon good"
	make test_multiple EX=$(EX) FLAGS="--protocol TCP --monitored --enable-loss --faulty" GATHER=gatherdataTCP.sh DATA="tcploss mon bad"

	rm -rf examples/$(EX)/log; mkdir examples/$(EX)/log; sudo mn -c

test_checksum_example:
	rm -f .example_logs/$(EX)*
	make clean
	make build_tcp
	sh testing/set_tcp_no_checksum.sh
	make test_multiple EX=$(EX) FLAGS="--protocol TCP --monitored" GATHER=gatherdataTamper.sh DATA="tcptamper nocheck good MonitoredTCP"
	make test_multiple EX=$(EX) FLAGS="--protocol TCP --monitored --tamper" GATHER=gatherdataTamper.sh DATA="tcptamper nocheck bad MonitoredTCPTamper"

	sh testing/set_tcp_checksum.sh
	make test_multiple EX=$(EX) FLAGS="--protocol TCP --monitored --checksum" GATHER=gatherdataTamper.sh DATA="tcptamper check good MonitoredTCPChecksum"
	make test_multiple EX=$(EX) FLAGS="--protocol TCP --monitored --tamper --checksum" GATHER=gatherdataTamper.sh DATA="tcptamper check bad MonitoredTCPTamperChecksum"

test_all_examples:
	make EX=bookreview test_example
	make EX=store test_example
	make EX=vpn test_example
	make EX=firewall test_example
	make EX=dns test_example
	make EX=auction test_example
	make EX=cdn test_example
	make EX=sip test_example
	make EX=pop3 test_example
	make EX=game test_example

test_all_checksum_examples:
	make EX=store test_checksum_example
	make EX=vpn test_checksum_example
	make EX=firewall test_checksum_example
	make EX=auction test_checksum_example
	make EX=sip test_checksum_example
	make EX=pop3 test_checksum_example
	make EX=game test_checksum_example

print_single_checksum_result:
	sh testing/print_checksum_results.sh $(EX) $(RUNS)

print_checksum_results:	
	make EX=store print_single_checksum_result
	make EX=vpn print_single_checksum_result
	make EX=firewall print_single_checksum_result
	make EX=auction print_single_checksum_result
	make EX=sip print_single_checksum_result
	make EX=pop3 print_single_checksum_result
	make EX=game print_single_checksum_result

show_all_barplots:
	python3 testing/parser.py bookreview barplot $(RUNS)
	python3 testing/parser.py store barplot $(RUNS)
	python3 testing/parser.py vpn barplot $(RUNS)
	python3 testing/parser.py firewall barplot $(RUNS)
	python3 testing/parser.py dns barplot $(RUNS)
	python3 testing/parser.py auction barplot $(RUNS)
	python3 testing/parser.py cdn barplot $(RUNS)
	python3 testing/parser.py sip barplot $(RUNS)
	python3 testing/parser.py pop3 barplot $(RUNS)
	python3 testing/parser.py game barplot $(RUNS)

show_all_checksum_barplots:
	python3 testing/parser.py store checksum_barplot $(RUNS)
	python3 testing/parser.py vpn checksum_barplot $(RUNS)
	python3 testing/parser.py firewall checksum_barplot $(RUNS)
	python3 testing/parser.py auction checksum_barplot $(RUNS)
	python3 testing/parser.py sip checksum_barplot $(RUNS)
	python3 testing/parser.py pop3 checksum_barplot $(RUNS)
	python3 testing/parser.py game checksum_barplot $(RUNS)

save_all_barplots:
	python3 testing/parser.py bookreview barplot $(RUNS) --save
	python3 testing/parser.py store barplot $(RUNS) --save
	python3 testing/parser.py vpn barplot $(RUNS) --save
	python3 testing/parser.py firewall barplot $(RUNS) --save
	python3 testing/parser.py dns barplot $(RUNS) --save
	python3 testing/parser.py auction barplot $(RUNS) --save
	python3 testing/parser.py cdn barplot $(RUNS) --save
	python3 testing/parser.py sip barplot $(RUNS) --save
	python3 testing/parser.py pop3 barplot $(RUNS) --save
	python3 testing/parser.py game barplot $(RUNS) --save

save_all_checksum_barplots:
	python3 testing/parser.py store checksum_barplot $(RUNS) --save
	python3 testing/parser.py vpn checksum_barplot $(RUNS) --save
	python3 testing/parser.py firewall checksum_barplot $(RUNS) --save
	python3 testing/parser.py auction checksum_barplot $(RUNS) --save
	python3 testing/parser.py sip checksum_barplot $(RUNS) --save
	python3 testing/parser.py pop3 checksum_barplot $(RUNS) --save
	python3 testing/parser.py game checksum_barplot $(RUNS) --save

print_all_stats:
	python3 testing/print_results.py bookreview $(RUNS); echo ""
	python3 testing/print_results.py store $(RUNS); echo ""
	python3 testing/print_results.py vpn $(RUNS); echo ""
	python3 testing/print_results.py firewall $(RUNS); echo ""
	python3 testing/print_results.py dns $(RUNS); echo ""
	python3 testing/print_results.py auction $(RUNS); echo ""
	python3 testing/print_results.py cdn $(RUNS); echo ""
	python3 testing/print_results.py sip $(RUNS); echo ""
	python3 testing/print_results.py pop3 $(RUNS); echo ""
	python3 testing/print_results.py game $(RUNS); echo ""

clean: build_clean
	rm -f *\~
	rm -f */*\~
	sudo mn -c -v critical

kick_the_tires:
	make test_example EX=bookreview RUNS=1
	python3 testing/parser.py bookreview barplot 1
