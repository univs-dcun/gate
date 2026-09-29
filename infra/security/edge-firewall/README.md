# gate-edge-firewall (UG-343)

운영 서버의 **인터넷 쪽 인터페이스에서 내부용 포트를 막는** systemd 서비스다. 서버에 설치된 파일과 같은 내용이다.

## 왜 필요한가

2026-09-29 확인했다.

- 운영 서버는 공인 IP 가 인터페이스(`ens9f3`)에 직접 붙어 있고, 앞단에 공유기나 방화벽이 없다.
- compose 가 `0.0.0.0` 에 게시한 포트가 **전부 인터넷에서 닿았다**: postgres·redis·discovery·config-server·gateway·웹·fxp, 그리고 SSH.
- SSH 는 비밀번호 로그인이 열려 있었고, 3일에 3만 건 넘는 대입 시도가 들어왔다.

Docker 가 게시한 포트는 **ufw 를 우회한다.** 그래서 Docker 가 제공하는 `DOCKER-USER` 체인에서 막는다. 판정 기준은 DNAT 전의 호스트 포트(`--ctorigdstport`)다. SSH 는 `INPUT` 체인에서 막는다.

## 영향 범위

막는 것은 `EXT_IF` 로 **들어오는** 트래픽뿐이다. 아래는 영향이 없다.

- 사내망 인터페이스로 들어오는 접속 (Jenkins 배포 SSH 포함)
- 컨테이너끼리의 통신
- nginx 가 `127.0.0.1:7080/7750/7751` 로 보내는 프록시 (api·demo·univsgate.com)

공개 트래픽은 nginx 80·443 으로 계속 들어온다.

## 설치 (운영 서버)

```bash
sudo install -m 0755 gate-edge-firewall.sh /usr/local/sbin/gate-edge-firewall.sh
sudo install -m 0644 gate-edge-firewall.service /etc/systemd/system/gate-edge-firewall.service
sudo systemctl daemon-reload
sudo systemctl enable --now gate-edge-firewall.service
```

## 확인

```bash
sudo journalctl -u gate-edge-firewall --no-pager | tail -12   # present:/added: 9줄
sudo iptables -S DOCKER-USER                                   # DROP 8개, 맨 끝에 RETURN
sudo iptables -S INPUT                                         # 22 DROP 1개
```

외부에서 막혔는지는 서버 안에서 확인할 수 없다. 맥을 휴대폰 핫스팟에 연결한 뒤 `nc -vz -w3 <공인IP> <port>` 가 timed out 인지 본다. 닫힌 포트(예: 7999)를 대조군으로 함께 본다.

## 동작

- **도는 시점**: 부팅할 때 docker 뒤에 돈다. docker 가 재시작되면 다시 돈다 (`PartOf=docker.service`).
- **멱등**: 이미 있는 규칙은 건너뛴다. 몇 번을 실행해도 중복이 생기지 않는다.
- **실패 조건**: `EXT_IF` 가 없으면 아무것도 적용하지 않고 실패한다. 인터페이스 이름이 바뀌었는데 조용히 "정상" 이 되는 것을 막는다. `DOCKER-USER` 가 120초 안에 생기지 않아도 실패한다.
- **멈출 때**: 규칙을 지우지 않는다. 막혀 있는 것이 기본 상태다.

## 포트를 바꿀 때 — 반드시

compose 에 게시 포트를 추가하거나 바꾸면, 스크립트 상단의 `DOCKER_HOST_PORTS` 를 함께 고친다. 그다음 서버에 다시 설치하고 `sudo systemctl restart gate-edge-firewall` 을 실행한다. **안 하면 새 포트는 인터넷에 그대로 열린다.**

## 되돌리기

```bash
sudo systemctl disable --now gate-edge-firewall.service
# 규칙은 남는다. 지우려면 추가한 명령의 -I 를 -D 로 바꿔 실행한다:
sudo iptables -D DOCKER-USER -i ens9f3 -p tcp -m conntrack --ctorigdstport 7432 -j DROP
```

## 이 스크립트가 다루지 않는 것

- dev·stage 서버: 포트 번호가 다르고(8080·8888…), 사무실 공유기 뒤에 있다. 따로 점검한다 (UG-343).
- 이 저장소 밖의 서비스 (`univs-platform-toss-gpu` 5200·5201·11948): 담당자에게 전달했다.
- 근본 대책: compose 의 게시 주소를 `127.0.0.1:` 이나 사내망 주소로 묶는다 (UG-343 남은 일).
