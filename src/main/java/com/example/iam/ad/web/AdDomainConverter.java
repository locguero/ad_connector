package com.example.iam.ad.web;

import com.example.iam.ad.domain.AdDomain;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Accepts {@code qa-ent}, {@code QA_ENT}, etc. as the {domain} path segment. */
@Component
public class AdDomainConverter implements Converter<String, AdDomain> {

    @Override
    public AdDomain convert(String source) {
        return AdDomain.valueOf(source.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
