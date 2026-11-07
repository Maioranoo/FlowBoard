package com.flowboard.backend.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Identifica o cliente para os limites por IP.
 *
 * No Render, a requisição passa pela Cloudflare e por um proxy antes de chegar aqui, então
 * getRemoteAddr() é o IP do proxy (igual para todo mundo). Onde está o IP verdadeiro:
 *  1. True-Client-IP / CF-Connecting-IP: preenchidos pela Cloudflare com o IP que ELA viu. Se o
 *     cliente mandar esses cabeçalhos, a Cloudflare os substitui. São a fonte mais confiável.
 *  2. X-Forwarded-For: cada proxy ACRESCENTA um IP no fim da lista, mas o começo da lista é o que o
 *     cliente mandou, ou seja, pode ser inventado. Por isso usamos o ÚLTIMO item, o que um proxy
 *     nosso escreveu, nunca o primeiro.
 *
 * (Comportamento do Render conferido em relatos da comunidade, não em documentação oficial.)
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        for (String header : new String[]{"True-Client-IP", "CF-Connecting-IP"}) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].strip();
        }
        return request.getRemoteAddr();
    }
}
