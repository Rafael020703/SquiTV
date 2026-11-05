# DNS do aplicativo — Android e Windows

Implementação local posterior à v1.25, ainda sem nova publicação.

## Acesso e escolhas

O login e a dashboard abrem diretamente esta tela, inclusive sem uma conta conectada. A escolha fica salva por instalação/variante. Abrir DNS não apaga o formulário de login; Voltar retorna sem alterar fullscreen.

| Opção | Comportamento |
| --- | --- |
| Desativado — usar DNS do aparelho | Desativa o resolvedor próprio para comparar com a resolução normal. |
| Automático | Padrão quando ainda não há escolha salva. Prefere a menor média medida nesta conexão; tenta o outro provedor se a resolução falhar. |
| Google | Usa Google para nomes públicos. |
| Cloudflare | Usa Cloudflare para nomes públicos. |

A seleção antiga de **Servidor DNS** continua escolhendo o endereço do provedor IPTV. É independente deste resolvedor.

Uma escolha explícita já salva, inclusive Desativado, é preservada. Um arquivo de preferência inválido continua sinalizado e protegido, com resolução pelo aparelho; não é tratado como uma instalação nova.

## Medição

Dois provedores são medidos em paralelo, com duas consultas reais por provedor (`example.com` e `www.iana.org`), sem o cache de resultados do app e usando relógio monotônico. A média em ms pode incluir o estabelecimento HTTPS na primeira consulta. Falhas aparecem como ausência de resposta, nunca como zero/sucesso. **Medir novamente** atualiza os valores.

Não há geolocalização nem envio de credenciais para medir. O resultado representa esta conexão; não mede largura de banda ou latência do servidor IPTV. O automático usa o último vencedor; sem medição válida tenta Cloudflare e Google. Novas resoluções podem agendar outra medição após quinze minutos, sem varredura quando o app está ocioso.

## Implementação e isolamento

O resolvedor Kotlin/JVM em `:shared` usa `okhttp-dnsoverhttps` na versão já fixada do OkHttp. Endpoints oficiais: [Google](https://developers.google.com/speed/public-dns/docs/doh) e [Cloudflare](https://developers.cloudflare.com/1.1.1.1/encryption/dns-over-https/make-api-requests/). IPs de inicialização explícitos evitam depender do DNS do aparelho para localizar os resolvedores.

- Não altera adaptadores, DNS privado, VPN ou proxy do sistema. URLs, hostname HTTPS e validação de certificados permanecem originais.
- Nomes curtos, IPs literais e sufixos `.local`, `.lan`, `.home.arpa`, `.internal` e `.localhost` usam resolução local. Nomes públicos consultados são enviados ao resolvedor escolhido.
- Cache limitado a 128 nomes/60 segundos, sem guardar falhas. Trocar/desativar limpa esse cache e descarta conexões HTTP ociosas registradas, preservando chamadas/transmissões ativas. Vale para novas conexões.
- Preferência da instalação, não da conta; não entra no backup do app nem na transferência/backup Android do sistema.

### Android

API/metadados/EPG, imagens Coil, ExoPlayer/Multi-view, downloads e atualizações consomem o resolvedor. Downloads têm cliente próprio sem os interceptadores de log do player, preservando User-Agent e redirects. SharedPreferences `app_dns` é lido na inicialização; a escolha só é confirmada depois de gravada.

### Windows

API, teste dos endereços do servidor, imagens, downloads e atualizações usam o resolvedor. `app-dns.json` fica na pasta da variante, com esquema/tamanho limitados e escrita atômica. Arquivo ilegível é preservado e sinalizado, sem sobrescrita por suposição.

LibVLC usa uma rede nativa. Quando o DNS do app está ativado, conexões **HTTP/HTTPS** usam uma ponte privada em `127.0.0.1`, porta aleatória, autenticação aleatória por processo, cabeçalhos/prazos limitados e até 32 conexões admitidas. Destinos são resolvidos pelo DNS do app. HTTPS usa túnel CONNECT, mantendo os bytes TLS entre LibVLC e o servidor original; não substitui certificados/hostname nem descriptografa vídeo. GET/HEAD preservam caminho, consulta, Range e autenticação do servidor; autenticação da ponte não é encaminhada ao servidor.

Não é serviço instalado e não modifica o proxy do Windows. **Desativado** usa a rede direta normal nas novas instâncias LibVLC. Arquivos locais não passam pela ponte. Protocolos nativos além de HTTP/HTTPS não foram adaptados nesta etapa.

Referências: [proxy Windows VLC 3.0.23](https://github.com/videolan/vlc/blob/3.0.23/src/win32/netconf.c) e [túnel HTTPS autenticado](https://github.com/videolan/vlc/blob/3.0.23/modules/access/http/tunnel.c).

## Verificação e limites

Comandos, resultados e hash debug ficam no registro local ignorado `docs/manutencao-windows.md`. Compilação/leitura das fontes não confirmam reprodução real. Não houve execução física Android/TV ou do player Windows nesta implementação, nem confirmação de que DNS era a causa do problema em um aparelho específico. A release instalada do Windows permanece intocada; versões/publicações não são alteradas nesta etapa.
