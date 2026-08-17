package com.example.videostreaming.admin;

import com.example.videostreaming.catalog.VideoRepository;
import com.example.videostreaming.transcoding.TranscodingJobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final VideoRepository videos;
    private final TranscodingJobRepository jobs;

    public AdminController(VideoRepository videos, TranscodingJobRepository jobs) {
        this.videos = videos;
        this.jobs = jobs;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("videos", videos.findAll(PageRequest.of(0, 50)).getContent());
        model.addAttribute("jobs", jobs.findAll(PageRequest.of(0, 50)).getContent());
        return "admin/dashboard";
    }
}
