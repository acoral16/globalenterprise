package com.alianza.finn.controllers;

import com.alianza.finn.model.Position;
import com.alianza.finn.model.ResponseVO;
import com.alianza.finn.model.SateliteVO;
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import com.lemmingapex.trilateration.TrilaterationFunction;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class TrilaterationController {

    /**
     * logger
     */
    private static final Logger log = LoggerFactory.getLogger(TrilaterationController.class);


    /**
     * Posición de los satelites
     */
    private static double[][] positions = new double[][] { { -500.0, -200.0 }, { 100.0, -100.0 }, { 500.0, 100.0 } };
    private double[] distances = new double[3];
    private List<SateliteVO> sat = new ArrayList<>();

    @PostMapping(path = "/topsecret")
    public ResponseVO topsecret(@RequestBody Map<String, List<SateliteVO>> satellites) {

        log.debug("Ejecutando: POST topsecret");
        sat =  satellites.get("satellites");

        for (SateliteVO s : sat) {
            switch (s.getName()) {
                case "kenobi":
                    distances[0] = s.getDistance();
                    break;
                case "skywalker":
                    distances[1] = s.getDistance();
                    break;
                case "solo":
                    distances[2] = s.getDistance();
                    break;
            }
        }

        return solve();
    }

    @PostMapping(path = "/topsecret_split/{satellite_name}")
    public void topsecretsplit(@RequestBody SateliteVO s, @PathVariable(value="satellite_name") final String satellite_name) {

        log.debug("Ejecutando: POST topsecret_split");
        if(sat.size() == 0){
            SateliteVO ss = new SateliteVO();
            sat.add(ss);
            sat.add(ss);
            sat.add(ss);
        }

        switch (satellite_name) {
            case "kenobi":
                sat.set(0, s);
                break;
            case "skywalker":
                sat.set(1, s);
                break;
            case "solo":
                sat.set(2, s);
                break;
        }
    }

    @GetMapping(path = "/topsecret_split")
    public ResponseVO topsecretsplitresponse() {
        log.debug("Ejecutando: GET topsecret_split");
        return solve();
    }

    private ResponseVO solve(){
        // the answer
        try {

            if(sat.size() == 0 || (sat.get(0).getDistance() == 0 && sat.get(1).getDistance() == 0 || sat.get(2).getDistance() == 0)){
                throw new StringIndexOutOfBoundsException ();
            } else {
                NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
                LeastSquaresOptimizer.Optimum optimum = solver.solve();

                double[] centroid = optimum.getPoint().toArray();
                ResponseVO r = new ResponseVO();
                r.setMessage(getMessage(sat.get(0).getMessage(), sat.get(1).getMessage(), sat.get(2).getMessage()));
                Position p = new Position();
                p.setX(centroid[0]);
                p.setY(centroid[1]);
                r.setPosition(p);
                System.out.println("X: " + centroid[0] + ", Y: " + centroid[1]);
                return r;
            }
        }
        catch (StringIndexOutOfBoundsException  ee){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Error. No hay suficiente información", ee
            );
        }
        catch (Exception e){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Ocurrio un error procesando la solicitud", e
            );
        }
    }

    /**
     * Construye el mensaje acorde a la información entregada por los satelites
     * @param arr1 Mensaje satelite 1
     * @param arr2 Mensaje satelite 2
     * @param arr3 Mensaje satelite 3
     * @return Mensaje completo
     */
    public static String getMessage(String[] arr1, String[] arr2, String[] arr3) {
        List<String[]> listaMensajes = new ArrayList<String[]>();
        listaMensajes.add(arr1);
        listaMensajes.add(arr2);
        listaMensajes.add(arr3);
        int tamanio = calculateMessageSize(listaMensajes);

        String msj = "";

        for (int i = 0; i < tamanio; i++) {


            List<String> linea = new ArrayList<String>();

            for (String[] strings : listaMensajes) {
                if (strings.length > i && strings[i] != null) {
                    linea.add(strings[i]);
                }
            }
            String valorPosicion = "";

            Map<String, Long> occurrences = linea.stream()
                    .collect(Collectors.groupingBy(w -> w, Collectors.counting()));

            for (Map.Entry<String, Long> entry : occurrences.entrySet()) {
                valorPosicion = entry.getKey();
            }

            msj += valorPosicion + " ";


            for (int j = 0; j < listaMensajes.size(); j++) {
                String[] strings = listaMensajes.get(j);

                if (strings.length > i && !strings[i].equals(valorPosicion)) {
                    if (strings.length != tamanio) {

                        addPos(strings, i, valorPosicion);
                    } else {

                        strings[i] = valorPosicion;
                    }
                }
            }

        }
        return msj.trim().replaceAll("\\s+", " ");
    }

    /**
     * Calcula el tamaño del mensaje
     * @param listaMensajes
     * @return
     */
    public static Integer calculateMessageSize(List<String[]> listaMensajes) {
        List<Integer> lstSize = new ArrayList<>();
        for (String[] strings : listaMensajes) {
            lstSize.add(strings.length);
        }
        Collections.sort(lstSize);
        int max_count = 1, res = lstSize.get(0);
        int curr_count = 1;

        for (int i = 1; i < lstSize.size(); i++) {
            if (lstSize.get(i) == lstSize.get(i - 1))
                curr_count++;
            else {
                if (curr_count > max_count) {
                    max_count = curr_count;
                    res = lstSize.get(i - 1);
                }
                curr_count = 1;
            }
        }

        if (curr_count > max_count) {
            max_count = curr_count;
            res = lstSize.get(lstSize.size() - 1);
        }

        return res;
    }

    /**
     * Agrega una posición al arreglo
     * @param array
     * @param pos
     * @param value
     */
    static void addPos(String[] array, int pos, String value) {
        String prevValue = value;
        for (int i = pos; i < array.length; i++) {
            String tmp = prevValue;
            prevValue = array[i];
            array[i] = tmp;
        }
    }

}
