/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.redes;

/**
 * @author flavio
 */

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RecebeDados extends Thread {

    private final int portaLocalReceber = 2001;
    private final int portaLocalEnviar = 2002;
    private final int portaDestino = 2003;
    float valor;
    private int ultimoAckEnviado = -1;


    private void enviaAck(boolean fim, int numeroSequencia) {

        try {
            InetAddress address = InetAddress.getByName("localhost");
            try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnviar)) {
                String sendString = fim ? "F" : "A" + numeroSequencia;

                byte[] sendData = sendString.getBytes();

                DatagramPacket packet = new DatagramPacket(
                        sendData, sendData.length, address, portaDestino);

                datagramSocket.send(packet);
            }
        } catch (SocketException ex) {
            Logger.getLogger(RecebeDados.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(RecebeDados.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    private void solicitaRetransmissao(int numeroSequencia){
        try {
            InetAddress address = InetAddress.getByName("localhost");
            try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnviar)) {
                String sendString = "R" + numeroSequencia;
                byte[] sendData = sendString.getBytes();
                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, portaDestino);
                datagramSocket.send(packet);
                System.out.println("Solicitação de retransmissão enviada para o pacote: " + numeroSequencia);
            }
        } catch (SocketException ex) {
            Logger.getLogger(RecebeDados.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(RecebeDados.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        try {
            DatagramSocket serverSocket = new DatagramSocket(portaLocalReceber);
            byte[] receiveData = new byte[1400];
            //iniciando o numero anterior em zero para guardar a ordem de envio
            int numeroAnterior = -1;
            try (FileOutputStream fileOutput = new FileOutputStream("saida")) {
                boolean fim = false;
                while (!fim) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(receivePacket);
                    System.out.println("dado recebido");

                    byte[] tmp = receivePacket.getData();

                      //probabilidade de 60% de perder
                    //gero um numero aleatorio contido entre [0,1]
                    Random gerador = new Random();
                    
                    valor = (gerador.nextFloat());

                    // Extração do número de sequência (4 primeiros bytes do tmp)
                    int numeroSequencia = ((tmp[0] & 0xff) << 24) + ((tmp[1] & 0xff) << 16) + ((tmp[2] & 0xff) << 8) + (tmp[3] & 0xff);
                    System.out.println("Numero de sequencia recebido " + numeroSequencia);
                    //se o numero for do tamanho da janela reinicia a janela
                    if(numeroSequencia == 3){
                        numeroSequencia = -1;
                        numeroAnterior = -1;
                    } else { 
                        //se numero cair no intervalo [0, 0,6)
                        if (valor > 0.6){
                            for (int i = 4; i < tmp.length; i = i + 4) {
                                int dados = ((tmp[i] & 0xff) << 24) + ((tmp[i + 1] & 0xff) << 16) + ((tmp[i + 2] & 0xff) << 8) + ((tmp[i + 3] & 0xff));
        
                                if (dados == -1) {
                                    fim = true;
                                    break;
                                }
                                fileOutput.write(dados);                            
                            }
                        
                            if (numeroSequencia != (numeroAnterior + 1)) {
                                System.out.println("Pacote fora de ordem. Esperado: " + (numeroAnterior + 1) + ", Recebido: " + numeroSequencia);
                                if (ultimoAckEnviado != numeroAnterior + 1) {          
                                    solicitaRetransmissao(numeroAnterior + 1);
                                    ultimoAckEnviado = numeroAnterior + 1;
                                }
                                }else{
                                    //se tiver envia o ack
                                    numeroAnterior = numeroSequencia;
                                    enviaAck(fim, numeroSequencia);
                                    System.out.println("ack enviado");
                            }
                    

                        }else{
                            System.out.println("ack perdido"); 
                            solicitaRetransmissao(numeroAnterior + 1);
                            
                        }
                    } 
                    //significa perda, logo, você não envia ACK
                    //para esse pacote, e não escreve ele no arquivo saida.
                    //se o numero cair no intervalo [0,6, 1,0]
                    //assume-se o recebimento com sucesso.

                }
            }
        } catch (IOException e) {
            System.out.println("Excecao: " + e.getMessage());
        }
    }
}
