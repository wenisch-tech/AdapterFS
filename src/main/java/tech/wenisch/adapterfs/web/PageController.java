package tech.wenisch.adapterfs.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tech.wenisch.adapterfs.config.AdapterFsProperties;
import tech.wenisch.adapterfs.fs.FilesystemService;

@Controller
public class PageController {
    private final FilesystemService files; private final AdapterFsProperties properties;
    public PageController(FilesystemService files, AdapterFsProperties properties) { this.files = files; this.properties = properties; }
    @GetMapping("/") String home(Model model) {
        model.addAttribute("exports", files.exports()); model.addAttribute("protocols", properties); return "index";
    }
    @GetMapping("/login") String login() { return "login"; }
}
