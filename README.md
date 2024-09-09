## Informações

Aluno: Gustavo Alves Silva
Disciplina: Paradigmas de Programação
Curso: Engenharia de Computação
Data: 13/08/2024
Resumo: Este é um app. Android simples capaz de medir o som de um ambiente.



## Descrição

Ele utiliza o microfone interno do dispositivo afim de medir o nível de som em decibéis.
O nível de som é exibido em um gráfico e em uma exibição de texto em "tempo real", além disso, há um histórico mostrando o nível de som dos últimos 5 minutos.



## Detalhes técnicos
O aplicativo é escrito em Kotlin e utiliza a biblioteca [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) para exibir o gráfico. 
Ele implementa o tema de cores dinâmico introduzido no Android 12, tanto no ícone quanto nas cores do aplicativo.
O aplicativo utiliza um serviço em primeiro plano para manter o microfone ativo mesmo quando o aplicativo está em segundo plano, permitindo que ele continue medindo o nível de som.
O valor de dB apresentado não é extremamente preciso, pois não é calibrado com uma referência de 0 dB e o `MediaRecorder.AudioSource.MIC` realiza algumas elaborações no áudio (`MediaRecorder.AudioSource.UNPROCESSED` não é utilizado, pois não é suportado em todos os dispositivos); mas é suficiente para ver a diferença entre um ambiente silencioso e um ambiente barulhento.
Algumas bibliotecas do Google são utilizadas para obter a altura da tela na rotação, a fim de escolher o layout de tela correto ([material3-window-size-class](https://developer.android.com/reference/kotlin/androidx/compose/material3/windowsizeclass/package-summary)) e para solicitar e gerenciar permissões em tempo de execução de forma fácil no Compose ([accompanist-permissions](https://google.github.io/accompanist/permissions/)), que depende das APIs do android.
