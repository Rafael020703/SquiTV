# Revisão de melhorias e otimizações — 30/10/2026

Revisão por leitura do código atual. Os achados abaixo ainda não foram corrigidos nesta etapa. Impactos de desempenho são oportunidades identificadas; não foram medidos FPS, bateria, RAM ou tempo de abertura.

## Prioridades

| Prioridade | Área | Evidência no código | Melhoria proposta e benefício esperado |
| --- | --- | --- | --- |
| Alta | Busca com consulta preenchida | SearchViewModel executa performSearch no init antes de inicializar _selectedType e os demais estados lidos pela função. | Reordenar a inicialização para eliminar o acesso a campos ainda não criados ao abrir uma busca preenchida. Falha identificada por leitura, sem reprodução em aparelho. |
| Alta | Resultados de busca desatualizados | Cada tecla inicia outro launch; a consulta anterior não é cancelada e pode publicar depois da atual. Limpar o texto também não cancela a tarefa em curso. | Aguardar uma pequena pausa de digitação, cancelar o trabalho antigo, publicar somente a consulta atual e distinguir erro de resultado vazio. |
| Alta | Histórico e continuar assistindo | WatchProgressEntity usa somente streamId como chave; consultas e gravações não incluem conta/perfil/tipo. | Migrar preservando registros e usar identidade por conta, perfil, tipo e ID. Evitar mistura de progresso entre conteúdos com IDs repetidos. |
| Alta | Busca textual | IptvDao faz JOIN da tabela FTS com iptv_streams por nome. Nomes iguais em registros distintos podem multiplicar os resultados. O modo ALL usa LIKE, enquanto outros caminhos usam FTS. | Relacionar por identidade da linha, alinhar a semântica da busca e tratar consultas curtas e caracteres especiais. |
| Média | Downloads entre contas | DownloadEntity e as operações do Media3 usam o ID simples do conteúdo. | Definir identidade por conta e tipo, reconciliando Room, índice do Media3 e arquivos existentes sem descartar downloads. |
| Média | Catálogos grandes | Há métodos com Pager no repositório, mas CategoryViewModel carrega listas inteiras por categoria e faz ordenação/filtro sobre elas. | Conectar as telas que usam listas completas à paginação, preservando posição, foco, favoritos e contexto da troca de canais. |
| Média | Escritas durante downloads | IptvApplication consulta e grava o registro por download a cada segundo; qualquer mudança de bytes habilita a gravação. Trabalhos de atualização são lançados independentemente. | Manter o progresso transitório em memória e persistir por intervalo/evento, serializando atualizações para evitar que uma gravação antiga sobrescreva um estado final. |
| Média | Inicialização e preferências | O coletor de todas as configurações reassocia os requisitos do DownloadManager a cada emissão e acessa o gerenciador já na inicialização. | Observar somente downloadWifiOnly com distinctUntilChanged e avaliar a inicialização do gerenciador preservando retomadas pendentes. Medir o custo antes/depois. |
| Média | Isolamento dos testes instrumentados | O fixture usa ContextWrapper com o mesmo delegate global preferencesDataStore; não demonstra isolamento quando o delegate já existe. | Disponibilizar DataStore e escopo próprios para o fixture e encerrá-los antes de limpar os arquivos. Nenhum teste foi executado nesta revisão. |
| Baixa | Cache de imagens e consultas | Imagens têm orçamento de 20% do heap e até 512 MB em disco; há índices individuais e consultas que filtram/ordenam por várias colunas. | Medir em dispositivos com pouca memória e com catálogo grande; ajustar limites e índices compostos conforme resultados, preservando downloads e preferências. |

## Um ponto que já está limitado

SyncWatchProgressUseCase já limita gravações regulares de progresso a um intervalo de 10 segundos. A chamada do player a cada 2 segundos não significa uma gravação no banco a cada chamada. Eventos de conclusão/forçados seguem regras próprias. A oportunidade mais direta de reduzir escritas repetidas está no acompanhamento de downloads.

## Ordem sugerida

1. Corrigir inicialização, cancelamento e associação dos resultados da busca.
2. Planejar a migração do histórico e a identidade dos downloads sem perda dos registros existentes.
3. Integrar paginação às listas completas e avaliar consultas com planos de execução reais.
4. Reduzir escritas e trabalho de preferências; medir inicialização e uso de memória para ajustar o cache.

A paginação é apropriada para carregar grandes conjuntos em partes, conforme a [documentação do Android](https://developer.android.com/topic/libraries/architecture/paging/v3-overview). Operadores como [mapLatest](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/map-latest.html) cancelam a transformação anterior quando chega uma nova entrada; o tratamento da busca deve também preservar o cancelamento e impedir publicação de resultados antigos.

## Alteração solicitada durante a revisão: botões de voltar

- O AppHeader passou a conter título, subtítulo e ações da tela, sem botão de retorno.
- Removidos os parâmetros onBack/showBackButton do cabeçalho e ajustadas 16 chamadas, incluindo prévia e telas de detalhes, atualização, pesquisa, EPG e downloads.
- Removido o botão Voltar separado do console de diagnóstico.
- Mantidos os caminhos de navegação do Android/controle remoto no código, sem criar controles invisíveis que recebam foco.
- Não foram removidas ações de reprodução nem os comandos de tempo do EPG.

Validação desta alteração:

```powershell
.\gradlew.bat :app:assembleDebug :app:compileReleaseKotlin --console=plain
```

**BUILD SUCCESSFUL em 33 segundos**, 50 tarefas, Configuration Cache reutilizado. APK debug gerado; Kotlin da variante release compilado. git diff --check sem problemas. Testes, lint e execução em aparelho não foram realizados novamente nesta alteração. A geração de APK release com R8 e o lint relatados no documento anterior pertencem à etapa anterior de reformulação do atualizador.

Log: `C:/Users/rafas/Documents/ChatGPT/Squi TV(Android)/build-remove-back-buttons.log`.

## Alteração posterior: aviso compacto ao trocar de canal

- ZappingBanner substituído por um cartão no canto superior esquerdo.
- Largura máxima de 360 dp em telas expandidas e 300 dp nas compactas, em lugar da faixa de até 85% da tela.
- Logo reduzido de 120/90 dp para 44/36 dp, com ícone de fallback caso a imagem não carregue.
- Nome do canal e programa em uma linha cada, com reticências para textos longos; número do canal discreto e progresso do programa de 2 dp.
- Removidos do aviso rápido a borda de destaque intensa, o selo de qualidade repetido e o texto de ausência de EPG.
- Decodificação do logo limitada ao tamanho exibido, sem transição de imagem durante trocas rápidas.
- Aviso oculto enquanto os controles completos, configurações ou lista expandida estão visíveis; abrir os controles dispensa o aviso. O Voltar trata o painel visível sem priorizar um aviso oculto.
- O cartão é informativo e não cria alvo de foco para o controle remoto.

Validação: `:app:assembleDebug :app:compileReleaseKotlin` — **BUILD SUCCESSFUL em 8 segundos**. Sem execução de testes, lint adicional ou inspeção em aparelho nesta alteração. O APK release otimizado existente ainda corresponde à etapa anterior; os ajustes posteriores estão no código e no APK debug.

Log: `C:/Users/rafas/Documents/ChatGPT/Squi TV(Android)/build-compact-zapping-banner.log`.

## Atualização durante a preparação da v1.19

A validação para publicação corrigiu três achados acima: inicialização da busca com texto preenchido, cancelamento/publicação de consultas antigas e vínculo da busca FTS por nome. Agora a busca inicia depois de criar seus estados, aguarda 250 ms de pausa na digitação, cancela a consulta anterior, invalida resultados antigos e relaciona o índice FTS pela identidade da linha. Foram adicionadas regressões com o ViewModel e banco Room reais no ambiente de testes.

Histórico e downloads por conta/perfil, paginação, ajustes de armazenamento e isolamento do fixture instrumentado continuam como prioridades futuras. Não houve mudança de esquema do banco nem migração desses dados nesta release.
