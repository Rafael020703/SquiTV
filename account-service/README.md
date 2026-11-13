# Conta Squi TV

O portal público ficará em **squitv.rafael020703.shop**. A homologação tem PostgreSQL, uma checagem local de prontidão, página inicial e identidade OIDC.

## Banco de homologação

O CT 105 usa PostgreSQL 16. O banco de perfis usa socket Unix. O esquema pertence ao papel sem login squitv_account_owner. A checagem roda como o usuário de sistema squitv_account_api, autenticado por peer. Esse papel lê apenas a tabela de versões. O banco separado de identidade aceita TLS na interface privada para o CT 106.

Depois de criar os papéis e o banco UTF-8, aplique a migração como administrador.

```sh
psql -X --set ON_ERROR_STOP=1 --single-transaction \
  --dbname squitv_account_pilot --file db/001_initial.sql
```

O endpoint exige o pacote python3-psycopg2. Execute o processo no CT 105 como squitv_account_api. Em outro terminal do mesmo container, consulte a rota pelo loopback.

```sh
runuser -u squitv_account_api -- python3 /usr/local/lib/squitv-account/health.py
curl --fail http://127.0.0.1:8765/health
```

O esquema associa perfil, vínculo e conteúdo à mesma conta com chaves estrangeiras compostas. As tabelas cobrem perfis, favoritos, progresso e categorias. O vínculo prevê armazenamento cifrado. A migração ainda não define a ordem de entrega de alterações concorrentes da futura sincronização.

## Índice do catálogo IPTV

A migração 010 cria um índice separado dos dados de conta e perfil. O serviço
`squitv-catalog-index` roda no CT 106 e usa o PostgreSQL no CT 105. Em cada
rodada processa até três catálogos, sequencialmente, em ordem de agendamento.
Vínculos com credenciais e sem expiração registrada, ou com validade futura,
entram na fila.
Cada snapshot fica restrito ao vínculo. Respostas inválidas preservam a última
versão e são reavaliadas com intervalo crescente. Após um sucesso, o próximo
levantamento é agendado para seis horas depois.

O índice guarda categorias, listas de canais, filmes e séries e os metadados
básicos da listagem. O painel usa o vínculo selecionado para resolver títulos
ausentes em favoritos e progresso e mostra o estado da preparação por catálogo.
O portal abre o catálogo em páginas de 24 itens, com busca por título,
capas e banners quando disponíveis. Conta e Minha lista ficam em telas próprias.

A migração 011 e o timer `squitv-catalog-metadata` preparam detalhes de
Filmes e Séries em lotes de até cinco consultas concorrentes. O EPG entra
na fila quando solicitado, com validade de dez minutos. Os apps autenticados
com a Conta Squi TV consultam o feed incremental de gêneros e reaproveitam
detalhes, episódios e EPG prontos. Enquanto um detalhe está na fila, a tela
correspondente pode consultá-lo no provedor. A listagem inicial e a reprodução
continuam pelo provedor. Nenhum conteúdo de vídeo é armazenado.
Quando uma série vem com ficha, mas sem episódios, o catálogo aproveita a ficha
e mantém a busca de episódios pendente. A API de detalhes continua permitindo
que os apps usem a consulta direta até o provedor devolver a lista de episódios.
O worker guarda as miniaturas fornecidas por episódio. Séries preparadas antes
dessa mudança são reprocessadas conforme a fila e ganham prioridade quando
abertas. Categorias do provedor com nomes explícitos de gênero servem de
fallback quando a ficha e as fontes externas não informam o gênero.

### Metadados públicos e TMDB

O worker consulta o TVmaze para séries com IMDb confirmado ou título e ano
com correspondência única, e o Wikidata
para filmes e séries identificados por IMDb ou TMDB. A fila limita as
consultas externas por rodada e mantém a origem e a licença de cada fonte.
O TVmaze pode preencher sinopse, gênero, capa e estreia. O Wikidata pode
preencher gênero, estreia, direção e elenco. A API preserva campos do
provedor, usa TVmaze antes de Wikidata para os campos restantes e deixa a
categoria explícita do provedor como último recurso para o gênero. Os apps
mostram os créditos gerais em Configurações, e o portal credita os detalhes.
Episódios, IDs e endereços
de reprodução continuam sendo os fornecidos pelo IPTV.

O worker pode completar sinopse, gênero, capa e banner de filmes e séries.
Ele preserva os campos do provedor e usa um ID TMDB informado pelo
provedor, uma correspondência única por IMDb ou uma correspondência única
por título e ano. Também pode preencher data de estreia e duração de filmes.
O painel mostra os
créditos quando recebe metadados do TMDB. A grade e o EPG do Ao vivo continuam
usando o provedor IPTV.

Para ativar, configure o API Read Access Token no CT 106 como credencial
systemd `tmdb-token` do serviço `squitv-catalog-metadata.service`. Mantenha
o arquivo da credencial legível apenas por root. No drop-in do serviço,
defina `SQUITV_TMDB_LOGO_URL` com a URL HTTPS de um logo aprovado pelo TMDB.
Recarregue as unidades e deixe a próxima rodada do timer usar a configuração.
Confira a licença adequada e a atribuição antes de ativar.

## Homologação em 11/11/2026

O CT 105 está em Ubuntu 24.04, com 1 vCPU, 1 GiB de RAM e 12 GiB de disco. O endereço é temporário por DHCP. PostgreSQL 16.15 atende pelo socket Unix, e a conexão TCP à porta 5432 foi recusada a partir do nó Proxmox. A checagem HTTP retornou 200 na rota de saúde quando executada temporariamente em loopback.

O teste restaurou um dump de dados fictícios em um banco separado e verificou perfis com acentos, favoritos, posição de reprodução e ordem de categorias. A cópia do dump de teste no Windows foi conferida por SHA-256. A rotina de backup para dados reais ainda precisa de agendamento e destino criptografado fora do host.

## Portal e identidade

| Container | IP na LAN | IP na rede privada vmbr27 |
|---|---|---|
| 105 · squitv-db | 192.168.2.248 | 172.31.105.1 |
| 106 · squitv-conta | 192.168.2.247 | 172.31.105.2 |

A bridge vmbr27 não tem porta física ou IPv4 no nó. O PostgreSQL escuta em 172.31.105.1 e a regra de rede do banco de identidade aceita apenas o papel squitv_keycloak vindo de 172.31.105.2 com TLS e SCRAM. O Keycloak valida a CA privada e o nome squitv-db.internal. O papel da identidade não acessa o banco de perfis. Senhas e chaves foram geradas nos containers e ficam fora do Git.

O CT 106 roda Keycloak 26.8.2 com JDK 21 e Nginx. O Nginx usa um certificado de homologação e aceita conexões em 127.0.0.1:443 e 192.168.2.247:443. Pela LAN, o acesso HTTP é restrito ao conector Cloudflare em 192.168.2.250. Keycloak e a interface de saúde escutam em loopback. O Nginx encaminha apenas o realm squitv e os recursos estáticos da identidade. O painel de administração permanece acessível localmente. O portal usa nome de usuário e senha, com recuperação feita pelo administrador.

Antes de usar a unidade de ops com uma instalação nova, prepare a distribuição com as opções de build abaixo. O realm em identity é importado quando ainda não existe.

```sh
/opt/keycloak/bin/kc.sh build --db=postgres --health-enabled=true --http-relative-path=/auth
```

Para conferir o portal e o documento de descoberta dentro do CT 106, use o nome definitivo com resolução local e confie somente no certificado de homologação. [Keycloak — banco com TLS](https://www.keycloak.org/server/db) · [Keycloak — proxy reverso](https://www.keycloak.org/server/reverseproxy)

```sh
curl --fail --cacert /etc/squitv-account/portal-cert.pem \
  --resolve squitv.rafael020703.shop:443:127.0.0.1 \
  https://squitv.rafael020703.shop/auth/realms/squitv/.well-known/openid-configuration
```

Foi validado o portal, a tela de entrada, a descoberta OIDC em HTTPS, a emissão do código temporário para TV e o estado de autorização pendente. As conexões ativas do Keycloak foram verificadas no PostgreSQL como TLS. As portas 443, 8080 e 9000 do portal e 5432 do banco foram recusadas na LAN a partir do nó. O teste de TV terminou antes da aprovação do dispositivo.

## Próxima etapa

O conector já acessa a origem HTTPS com certificado validado. A rota do túnel deve usar https://192.168.2.247:443, com Origin Server Name squitv.rafael020703.shop e CA Pool /etc/cloudflared/squitv-origin.pem no CT 102. O certificado de homologação vence em 26/12/2026.

As duas builds debug oferecem **Conta Squi TV · teste**. O app solicita um código de dispositivo, abre a aprovação no navegador e informa se o login foi confirmado. Após a autorização OIDC, o app consulta o vínculo IPTV e inicia o login normal do provedor. A sincronização de perfis ainda precisa ser implementada.

A aprovação completa do dispositivo ainda precisa ser exercitada com uma conta. O vínculo pelo portal está descrito abaixo.

## Cadastro local e recuperação

No console do CT 106, execute os scripts preparados para o proprietário. O
primeiro cria um administrador temporário quando o realm master ainda não tem
administrador. O Keycloak solicita a senha no próprio terminal e o serviço
reinicia ao terminar. Se já houver administrador, pule esse comando.

```sh
/usr/local/lib/squitv-account/bootstrap-owner-console.sh &&
/usr/local/lib/squitv-account/enable-local-registration.sh squitv-admin-temporario
```

O segundo comando usa a API administrativa pelo loopback para ocultar e-mail e
nome do formulário, desligar o login e a recuperação por e-mail e abrir o
cadastro do realm squitv. Confirme
a presença de Registrar no login e que o formulário pede usuário, senha e
confirmação da senha.

Para a administração cotidiana, crie uma conta permanente separada no realm
squitv, atribua a função manage-users do cliente realm-management e teste o
acesso pelo console local ou pelo kcadm. Remova a conta temporária do realm
master após confirmar o acesso permanente. Para recuperar uma conta, confirme
a identidade com o solicitante, encerre as sessões anteriores e defina uma
senha temporária que o usuário troca no próximo login. O Keycloak mantém a
senha como hash. Não exponha a rota /auth/admin no túnel.

- [Bootstrap administrativo](https://www.keycloak.org/server/bootstrap-admin-recovery)
- [Administração de usuários](https://www.keycloak.org/docs/26.8.1/server_admin/)
## Portal próprio

A interface pública está em portal/. A página de conta usa OAuth Authorization
Code com PKCE e troca o código no mesmo domínio. O estado e o verificador ficam
na sessão da aba até a conclusão. Os tokens ficam em memória. O Nginx entrega
a página própria no endereço de retorno já registrado no cliente account-console
e desabilita os caminhos públicos do painel de conta antigo.
Para publicar o portal, execute python account-service/package_portal.py <pasta-de-saída>.
Copie assets/ primeiro e index.html por último para /var/www/squitv-account.
Os nomes por conteúdo impedem que o cache da Cloudflare reutilize um JavaScript antigo.

O cliente de dispositivo squitv-tv continua no mesmo realm.

As páginas de autenticação foram implementadas em identity/themes/squitv/login.
Depois de copiar essa pasta para /opt/keycloak/themes/squitv/login, selecione o
tema no console do CT 106 com uma conta administrativa local.

```sh
/usr/local/lib/squitv-account/activate-portal-theme.sh squitv-admin-temporario
```

O script solicita a senha no terminal. Verifique no domínio público se entrar,
criar conta e alterar senha usam o visual Squi TV. O serviço de autenticação e
os usuários existentes continuam no Keycloak local.
## Vínculo IPTV

O painel Conta IPTV aparece após entrar no portal. O serviço squitv-iptv
no CT 106 autentica a sessão pelo UserInfo do realm local e valida o acesso
no endpoint HTTPS fixo do provedor autorizado. O formulário aceita usuário
e senha. A resposta pública indica somente se o vínculo existe.

A migração db/002_iptv_link.sql concede acesso às colunas necessárias ao papel
squitv_iptv_portal. O banco no CT 105 aceita esse papel apenas a partir de
172.31.105.2 com TLS e SCRAM. A unidade systemd recebe a senha do banco e a
chave AES-256-GCM por LoadCredential. Preserve a chave fora do Git e dos
backups do banco e mantenha uma cópia protegida para recuperação. O dado
cifrado está associado ao identificador da conta e à versão da chave.

Vincular novamente o mesmo usuário IPTV atualiza a senha. Trocar para
outro usuário IPTV exige uma política explícita de migração dos dados.
O endpoint GET /api/iptv/credentials exige um token válido do cliente squitv-tv e lê apenas o vínculo do titular autenticado. O portal usa GET e POST em /api/iptv/link com o cliente account-console. As respostas não são armazenadas em cache.

Android e Windows usam a ordem padrão de servidores do aplicativo após recuperar o vínculo. A validação do vínculo pelo portal usa HTTPS. Uma conta sem vínculo recebe a orientação para vinculá-la no portal. O token OIDC fica apenas em memória.
## Login direto no Android e no Windows

O formulário do app envia usuário e senha da Conta Squi TV por HTTPS
para POST /api/iptv/native-login. O serviço no CT 106 autentica com
o cliente confidencial squitv-native-login do Keycloak pelo loopback
e retorna somente o vínculo do titular. O segredo desse cliente fica
no CT 106 e é entregue ao serviço por LoadCredential. O fluxo por
código continua disponível no diálogo. A tela oferece a Conta Squi TV
e o acesso IPTV direto nas variantes debug e release. O acesso direto
respeita o endereço informado pelo usuário.

Para ativar em um ambiente com o serviço e a unidade preparada, execute
no console do CT 106 com uma conta administrativa.

```sh
/usr/local/lib/squitv-account/activate-native-login.sh squitv-admin-temporario
```

O script solicita a senha no terminal, configura o cliente confidencial
e reinicia o serviço de vínculo. A autenticação da Conta Squi TV é HTTPS
entre o app e o portal. A conexão IPTV usa os endereços padrão do app,
começando pelo servidor primário configurado.
## Dados da dashboard

A migração db/003_iptv_dashboard.sql acrescenta a data da última
validação bem-sucedida e a validade informada pelo provedor.
Vínculos anteriores exibem datas indisponíveis até a próxima
validação. O resumo do portal mostra o usuário IPTV e o estado do
vínculo. A senha é retornada apenas pelo endpoint explícito de revelação.
A dashboard consulta GET /api/iptv/details com a sessão do titular.
O serviço usa o vínculo cifrado no servidor e retorna situação, validade,
conexões e horário da consulta. A leitura não altera o vínculo.

## Gerenciamento dos dados do app

A dashboard lista apenas vínculos cadastrados e permite adicionar até 20
logins do provedor. O app conecta diretamente quando existe um vínculo e
oferece a escolha quando existem vários. O nome exibido durante o login
pela Conta Squi TV vem da identidade do portal. O endpoint de revelação
da senha IPTV exige a sessão do titular e uma ação no painel. Atualizar
um vínculo envia só a senha nova para o usuário IPTV já registrado.

As migrações 004 a 008 preservam os identificadores dos vínculos, adicionam
posições e habilitam favoritos, categorias e progresso por conta, vínculo
e perfil. O portal publica os perfis, oferece criação e alteração de nome,
lista favoritos e progresso, permite removê-los e permite ocultar ou
reordenar categorias. O endpoint de sincronização recebe a sessão do
portal ou um token temporário restrito ao vínculo escolhido no app.

No app debug, entre pela Conta Squi TV e abra Configurações → Sincronizar agora. Escolha
explicitamente o perfil do portal e confirme a importação inicial. A
associação e a revisão ficam no aparelho. Importações repetidas não
reativam itens removidos. O Android associa o perfil local ativo.
O Windows associa sua lista local ao perfil selecionado.

Sincronizar agora atualiza favoritos, progresso novo e organização de
categorias. Se a organização foi alterada dos dois lados, o diálogo
preserva as alterações e oferece a escolha de qual organização aplicar.
O progresso antigo permanece no aparelho. Após a associação, Android
e Windows sincronizam favoritos e progresso novo ao abrir a sessão e
voltam a consultar o painel a cada minuto com o app ativo. A fila de
progresso também é enviada após 15 segundos sem novos registros. Em caso
de falha, fica no aparelho para a próxima tentativa. A organização de
categorias continua acessível na ação manual.
O portal mostra a organização já salva mesmo quando o provedor está
indisponível para consultar os nomes das categorias.
A sessão temporária de sincronização é armazenada com Android Keystore
ou Windows DPAPI quando o login é lembrado. Após expirar, entre novamente
pela Conta Squi TV.

Ao publicar o tema, copie o CSS versionado da pasta assets/ e os arquivos
empacotados de theme/login/ para /opt/keycloak/themes/squitv/login.
A raiz do portal inicia a autenticação imediatamente, e o cadastro fica
na própria tela de login.

O campo Nome de exibição no painel é opcional e separado do usuário de login.
O nome salvo é usado na próxima entrada no Android ou Windows pela Conta
Squi TV. A edição de categorias aceita busca por nome e código. As mudanças
de favoritos feitas nos apps são enviadas em lotes de até 20, e o diálogo
informa quando manteve uma revisão mais recente de outro dispositivo.
