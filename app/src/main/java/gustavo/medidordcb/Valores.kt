@file:JvmName("Values")
package gustavo.medidordcb

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.log10


object Valores {
    // Nome da classe usado para logs
    private val TAG = Valores::class.simpleName

    // Filas que armazenam valores recentes dos canais esquerdo e direito.
    // Novos itens são adicionados no final, e quando exibidos em gráficos, eles são removidos do início.
    private var leftQueue : ConcurrentLinkedQueue<Float> = ConcurrentLinkedQueue<Float>()
    private var rightQueue : ConcurrentLinkedQueue<Float> = ConcurrentLinkedQueue<Float>()

    // Último elemento removido de cada fila (usado para exibição ou processamento adicional).
    var lastLeft : Float = 0f
    var lastRight : Float = 0f

    // Listas que armazenam os valores de amplitude do último segundo (tamanho máximo = 1 segundo = SAMPLE_RATE / 60).
    var lastSecDbLeftList = mutableListOf<Float>()
    var lastSecDbRightList = mutableListOf<Float>()

    // Contadores para o número de itens adicionados às listas do último segundo.
    private var leftCount = 0
    private var rightCount = 0

    // Listas que armazenam os valores máximos dos últimos 5 minutos (tamanho máximo = 5 minutos = 1 * 60 * 5).
    var last5MinDbLeftList = mutableListOf<Float>()  //tamanho máximo = 5 minutos = 1*60*5
    var last5MinDbRightList = mutableListOf<Float>()  // tamanho máximo = 5 minutos = 1*60*5


    /**
     * Insere os valores medidos recentemente nas filas (para serem exibidos).
     * Ignora automaticamente valores de infinito negativo e reamostra para 60Hz.
     *
     * @param leftMeasuredValues valores medidos do canal esquerdo
     * @param rightMeasuredValues valores medidos do canal direito
     * @param readN número bruto de valores lidos do buffer (retornado por [`AudioRecord.read()`])
     */
    fun updateQueues(leftMeasuredValues: FloatArray, rightMeasuredValues: FloatArray, readN: Int) {
        // Reamostra os valores para 60Hz e remove valores de infinito negativo.
        val leftValues = downsampleTo60Hz(leftMeasuredValues.sliceArray(0 until readN/2)).filter { it!=Float.NEGATIVE_INFINITY }     // downsample to 60Hz (from 44100Hz) and take only the read part of the array
        leftQueue.addAll(leftValues)
        val rightValues = downsampleTo60Hz(rightMeasuredValues.sliceArray(0 until readN/2)).filter { it!=Float.NEGATIVE_INFINITY }
        rightQueue.addAll(rightValues)
        Log.d(TAG, "updateQueues: leftQueue size: ${leftQueue.size} rightQueue size: ${rightQueue.size}")
    }

    /**
     * Adiciona um valor ao final da lista de amostras dos últimos 5 minutos (canal esquerdo).
     * Se a lista estiver cheia, o primeiro elemento é removido.
     *
     * @param newMaxLeft novo valor a ser adicionado à lista
     */
    private fun updateLast5MinDbLeft(newMaxLeft: Float) {
        // Remove elementos antigos se a lista estiver cheia
        while (last5MinDbLeftList.size > 1*60*5) { last5MinDbLeftList.removeFirst() }
        last5MinDbLeftList.add(newMaxLeft)
    }

     /**
     * Adiciona um valor ao final da lista de amostras dos últimos 5 minutos (canal direito).
     * Se a lista estiver cheia, o primeiro elemento é removido.
     *
     * @param newMaxRight novo valor a ser adicionado à lista
     */
    private fun updateLast5MinDbRight(newMaxRight: Float) {
        // Remove elementos antigos se a lista estiver cheia.
        while (last5MinDbRightList.size > 1*60*5) { last5MinDbRightList.removeFirst() }
        last5MinDbRightList.add(newMaxRight)
    }

     /**
     * Obtém os valores máximos das amostras do último segundo (canais esquerdo e direito).
     *
     * @return array de dois valores: valores máximos esquerdo e direito
     */
    fun getMaxDbLastSec() : FloatArray {
        // Obtém o valor máximo do último segundo para o canal esquerdo e o adiciona à lista dos últimos 5 minutos.
        val maxLeft = lastSecDbLeftList.maxOrNull() ?: if (lastSecDbLeftList.size == 1) lastSecDbLeftList.first() else 0f
        updateLast5MinDbLeft(maxLeft)
        // Obtém o valor máximo do último segundo para o canal direito e o adiciona à lista dos últimos 5 minutos.
        val maxRight = lastSecDbRightList.maxOrNull() ?: if (lastSecDbRightList.size == 1) lastSecDbRightList.first() else 0f
        updateLast5MinDbRight(maxRight)
        // Log.d(TAG, "getMaxDbLastSec: $maxLeft, $maxRight, sizes: ${lastSecDbLeftList.size}, ${lastSecDbRightList.size}")
        return floatArrayOf(maxLeft, maxRight)
    }

     /**
     * Retorna o primeiro elemento da fila e o adiciona à lista de elementos já removidos (canal esquerdo).
     * A cada segundo, a lista é esvaziada e o valor máximo é adicionado à lista das amostras dos últimos 5 minutos.
     *
     * @return primeiro elemento da fila esquerda
     */
    fun getFirstFromQueueLeft() : Float? {
        Log.d(TAG, "getFirstFromQueueLeft: leftQueue size: ${leftQueue.size} rightQueue size: ${rightQueue.size}")
        // Remove e retorna o primeiro elemento da fila do canal esquerdo.
        val out = leftQueue.poll()
        if ( out != null ) {
            leftCount++
            lastSecDbLeftList.add(out)
        }

        // Se um segundo de dados foi processado, calcula o valor máximo e limpa a lista.
        if (leftCount >= ServicoMedicao.SAMPLE_RATE / 60) {
            last5MinDbLeftList.add(lastSecDbLeftList.maxOrNull() ?: if (lastSecDbLeftList.size == 1) lastSecDbLeftList.first() else 0f)
            leftCount = 0
            lastSecDbLeftList.clear()
        }

        lastLeft = out ?: 0f
        // Log.d(TAG, "getFirstFromQueueLeft: $out")
        return out
    }

     /**
     * Retorna o primeiro elemento da fila e o adiciona à lista de elementos já removidos (canal direito).
     * A cada segundo, a lista é esvaziada e o valor máximo é adicionado à lista das amostras dos últimos 5 minutos.
     *
     * @return primeiro elemento da fila direita
     */
    fun getFirstFromQueueRight() : Float? {
        // Remove e retorna o primeiro elemento da fila do canal direito.
        val out = rightQueue.poll()
        if ( out != null ) {
            rightCount++
            lastSecDbRightList.add(out)
        }

        // Se um segundo de dados foi processado, calcula o valor máximo e limpa a lista.
        if (rightCount >= ServicoMedicao.SAMPLE_RATE / 60) {
            last5MinDbRightList.add(lastSecDbRightList.maxOrNull() ?: if (lastSecDbRightList.size == 1) lastSecDbRightList.first() else 0f)
            rightCount = 0
            lastSecDbRightList.clear()
        }

        lastRight = out ?: 0f
        // Log.d(TAG, "getFirstFromQueueLeft: $out")
        return out
    }

     /**
     * Reseta todas as filas, listas e contadores.
     */
    fun resetAll() {
        leftQueue.clear()
        rightQueue.clear()
        lastLeft = 0f
        lastRight = 0f
        lastSecDbLeftList.clear()
        lastSecDbRightList.clear()
        leftCount = 0
        rightCount = 0
        last5MinDbLeftList.clear()
        last5MinDbRightList.clear()
    }


     /**
     * Reamostra o array para 60Hz (a partir de [ServicoMedicao.SAMPLE_RATE]).
     *
     * @param originalArray array a ser reamostrado
     * @return array reamostrado
     */
    private fun downsampleTo60Hz(originalArray: FloatArray): FloatArray {
        val originalSampleRate = ServicoMedicao.SAMPLE_RATE
        val targetSampleRate = 60
        val downsampleFactor = originalSampleRate / targetSampleRate
        val downsampledArraySize = originalArray.size / downsampleFactor
        val downsampledArray = FloatArray(downsampledArraySize)
        // Log.d(TAG, "downsampleTo60Hz: originalArray size: ${originalArray.size} downsampledArray size: ${downsampledArray.size}")

        // Seleciona apenas os índices necessários para reamostrar para 60Hz.
        for (i in 0 until downsampledArraySize) {
            val originalIndex = (i * downsampleFactor)
            downsampledArray[i] = originalArray[originalIndex]
        }

        return downsampledArray
    }

     /**
     * Converte um valor PCM para dB.
     *
     * @param pcm valor a ser convertido
     * @return valor em dB
     */
    fun pcmToDb(pcm: Number) : Float {
        return 20 * log10( (abs(pcm.toFloat()) /32768) / 20e-6f)   // This value is not calibrated
    }
}