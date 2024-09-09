package gustavo.medidordcb

import android.util.Log
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

/**
 * Um gráfico Composable baseado na biblioteca MPAndroidChart.
 * É um wrapper em torno da classe LineChart.
 * É usado para desenhar os gráficos no aplicativo.
 * Pode ser de diferentes tipos, cada um com suas próprias configurações: [ONE_SEC_LEFT], [ONE_SEC_RIGHT], [FIVE_MIN_LEFT], [FIVE_MIN_RIGHT].
 *
 * Usado ao acionar seu operador [invoke].
 */
enum class Graficos {
    // Enumerações para representar diferentes tipos de gráficos
    ONE_SEC_LEFT, ONE_SEC_RIGHT,
    FIVE_MIN_LEFT, FIVE_MIN_RIGHT;

    // Propriedade lateinit para armazenar o gráfico (LineChart) correspondente a cada enumeração
    private lateinit var chart: LineChart


    /**
     * Desenha o gráfico do tipo especificado em [Graficos].
     *
     * @param updateTrigger o valor que aciona a recomposição do gráfico (é um truque para acioná-lo)
     * @param modifier o modificador a ser aplicado ao gráfico
     */
    @Composable
    operator fun invoke(updateTrigger : Float, modifier: Modifier = Modifier){
        // Armazena o tipo de gráfico atual
        val type = this

        // Define o número máximo de entradas no gráfico com base no tipo
        val maxEntries = when(type) {
            ONE_SEC_LEFT, ONE_SEC_RIGHT -> 60 // 1 minuto de dados (60 segundos)
            FIVE_MIN_LEFT, FIVE_MIN_RIGHT -> 60*5 // 5 minutos de dados (300 segundos)
        }

        // Esquema de cores (supõe-se que seja uma variável definida em outro lugar)
        val colorScheme = colorScheme

        // Cria e configura o AndroidView que irá renderizar o LineChart
        AndroidView(
            modifier = modifier,
            factory = { context ->
                // Cria um novo LineChart e configura suas propriedades
                LineChart(context).apply {
                    chart = this // Armazena a instância do gráfico
                    chart.setTouchEnabled(false) // Desabilita a interação do usuário
                    chart.setDrawGridBackground(false) // Remove o fundo da grade
                    chart.setDrawBorders(false) // Remove bordas do gráfico
                    chart.setBackgroundColor(colorScheme.background.toArgb()) // Define a cor de fundo
                    chart.description.isEnabled = false // Desabilita a descrição
                    chart.legend.isEnabled = false // Desabilita a legenda
                    chart.axisRight.isEnabled = false // Desabilita o eixo da direita
                    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM // Posiciona o eixo X na parte inferior
                    chart.xAxis.setDrawLabels(false) // Remove rótulos do eixo X
                    chart.axisLeft.textColor = colorScheme.onBackground.toArgb() // Define a cor do texto do eixo Y
                    chart.isFocusable = false // Desabilita foco no gráfico
                    chart.isClickable = false // Desabilita cliques no gráfico
                    chart.isLongClickable = false // Desabilita longos cliques
                    chart.isDoubleTapToZoomEnabled = false // Desabilita zoom por duplo toque
                    //chart.isAutoScaleMinMaxEnabled = true // Comentado: opção para auto-escalar min/max dos eixos
                    chart.axisLeft.axisMinimum = 0f // Define o mínimo do eixo Y
                    chart.axisLeft.axisMaximum = 120f // Define o máximo do eixo Y
                    chart.xAxis.axisMinimum = 0f // Define o mínimo do eixo X
                    chart.xAxis.axisMaximum = maxEntries.toFloat() // Define o máximo do eixo X
                    chart.xAxis.setLabelCount(7, true) // Define o número de rótulos no eixo X
                    chart.setMaxVisibleValueCount(0) // Define o número máximo de valores visíveis


                    // Cria um novo conjunto de dados (LineDataSet) para o gráfico
                    val dataSet = LineDataSet(mutableListOf<Entry>(), "") // adicionar entradas ao conjunto de dados
                    dataSet.color = colorScheme.primary.toArgb() // Define a cor da linha
                    dataSet.setDrawCircles(false) // Remove os círculos nos pontos de dados
                    dataSet.lineWidth = 3f // Define a largura da linha

                    // Associa o conjunto de dados ao gráfico
                    val lineData = LineData(dataSet)
                    chart.data = lineData

                    // Redesenha o gráfico com base nos dados atuais
                    redesenhar()
                }
            },
            update = { chart ->
                // Função para atualizar o gráfico com novos dados
                //  chart.clear()
                val data: LineData = chart.data
                val set = data.getDataSetByIndex(0) // Obtém o conjunto de dados
                lateinit var newEntry : Entry
                Log.d(TAG, "Chart: update $type, ${set.entryCount}, $updateTrigger")
                try{
                    // Cria uma nova entrada (Entry) com base no tipo de gráfico e nos dados fornecidos
                    newEntry = when (type) {
                        ONE_SEC_LEFT -> Entry(set.entryCount.toFloat(), updateTrigger)
                        ONE_SEC_RIGHT -> Entry(set.entryCount.toFloat(), updateTrigger)
                        FIVE_MIN_LEFT -> Entry(set.entryCount.toFloat(), Valores.last5MinDbLeftList[set.entryCount])
                        FIVE_MIN_RIGHT -> Entry(set.entryCount.toFloat(), Valores.last5MinDbRightList[set.entryCount])
                    }
                    Log.d(TAG, "Chart: newEntry: $newEntry, $type")
                } catch (e : Exception){
                    // Captura exceções e sai da função em caso de erro
                    Log.w(TAG, "Chart: exception: $e, $type")
                    return@AndroidView
                }

                // Adiciona a nova entrada ao conjunto de dados
                data.addEntry(newEntry, 0)

                // Remove a entrada mais antiga se o conjunto de dados exceder o máximo permitido
                if (set.entryCount > maxEntries){
                    set.removeEntry(0)
                    for (i in 1 until set.entryCount){
                        set.getEntryForIndex(i).x = i - 1f
                    }
                }

                // Notifica o gráfico de que os dados foram alterados e solicita uma atualização visual
                chart.notifyDataSetChanged()    // informar ao gráfico que seus dados mudaram
                chart.invalidate()
                Log.d(TAG, "Chart: updated $type")
            }
        )
    }


     /**
     * Redesenha o gráfico.
     * Deve ser usado quando as atualizações do gráfico não foram calculadas pela função de atualização por um tempo (por exemplo, quando o aplicativo passa por [Activity.onResume][android.app.Activity.onResume]).
     */
    fun redesenhar(){
        Log.d(TAG, "Chart: redraw $this")
        if (!this::chart.isInitialized)
            return

         // Obtém o conjunto de dados do gráfico e limpa as entradas atuais
        val dataSet = chart.data.getDataSetByIndex(0)
        dataSet.clear()

         // Preenche o conjunto de dados com base nos valores armazenados (presumivelmente em `Values`)
        when (this) {
            ONE_SEC_LEFT -> Valores.lastSecDbLeftList.forEachIndexed { index, value -> dataSet.addEntry(Entry(index.toFloat(), value)) }
            ONE_SEC_RIGHT -> Valores.lastSecDbRightList.forEachIndexed { index, value -> dataSet.addEntry(Entry(index.toFloat(), value)) }
            FIVE_MIN_LEFT -> Valores.last5MinDbLeftList.forEachIndexed { index, value -> dataSet.addEntry(Entry(index.toFloat(), value)) }
            FIVE_MIN_RIGHT -> Valores.last5MinDbRightList.forEachIndexed { index, value -> dataSet.addEntry(Entry(index.toFloat(), value)) }
        }

         // Notifica o gráfico de que os dados foram alterados e solicita uma atualização visual
        chart.notifyDataSetChanged()
        chart.invalidate()
    }


    companion object {
        // Constante para armazenar o nome da classe (usado para logging)
        private val TAG = Graficos::class.simpleName
    }
}
